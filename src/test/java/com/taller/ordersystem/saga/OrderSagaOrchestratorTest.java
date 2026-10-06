package com.taller.ordersystem.saga;

import com.taller.ordersystem.inventory.application.command.InventoryCommandService;
import com.taller.ordersystem.inventory.application.command.ReservationLine;
import com.taller.ordersystem.inventory.domain.InsufficientStockException;
import com.taller.ordersystem.order.application.command.OrderCommandService;
import com.taller.ordersystem.order.application.query.OrderItemView;
import com.taller.ordersystem.order.application.query.OrderQueryService;
import com.taller.ordersystem.order.application.query.OrderView;
import com.taller.ordersystem.order.domain.OrderStatus;
import com.taller.ordersystem.payment.application.PaymentCommandService;
import com.taller.ordersystem.payment.domain.PaymentRejectedException;
import com.taller.ordersystem.payment.domain.PaymentSimulationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del orquestador: verifican el ORDEN de los pasos y de las compensaciones
 * sin contenedor ni base de datos (los servicios transaccionales se sustituyen por mocks).
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final BigDecimal TOTAL = new BigDecimal("20.00");

    @Mock
    OrderQueryService orderQueries;
    @Mock
    OrderProcessService processes;
    @Mock
    OrderProcessQueryService processQueries;
    @Mock
    InventoryCommandService inventory;
    @Mock
    PaymentCommandService payments;
    @Mock
    OrderCommandService orders;

    @InjectMocks
    OrderSagaOrchestrator saga;

    @BeforeEach
    void pendingOrder() {
        Instant now = Instant.now();
        OrderView order = new OrderView(ORDER_ID, "customer-1", OrderStatus.PENDING, now, now, TOTAL,
                List.of(new OrderItemView(10L, PRODUCT_ID, 2, new BigDecimal("10.00"), TOTAL)));
        when(orderQueries.getById(ORDER_ID)).thenReturn(order);
    }

    @Test
    void happyPathReservesPaysConfirmsAndCompletesWithoutCompensations() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING)).thenReturn(SagaMode.FORWARD);

        saga.process(ORDER_ID, PaymentSimulationMode.APPROVE);

        InOrder flow = inOrder(processes, inventory, payments, orders);
        flow.verify(processes).moveTo(ORDER_ID, SagaStep.RESERVE_INVENTORY);
        flow.verify(inventory).reserve(ORDER_ID, List.of(new ReservationLine(PRODUCT_ID, 2)));
        flow.verify(processes).moveTo(ORDER_ID, SagaStep.PROCESS_PAYMENT);
        flow.verify(payments).charge(ORDER_ID, TOTAL, PaymentSimulationMode.APPROVE);
        flow.verify(processes).moveTo(ORDER_ID, SagaStep.CONFIRM_ORDER);
        flow.verify(orders).confirm(ORDER_ID);
        flow.verify(processes).markCompleted(ORDER_ID);

        verify(processes, never()).startCompensation(any(), anyString());
        verify(inventory, never()).release(any());
        verify(orders, never()).cancel(any());
    }

    @Test
    void rejectedPaymentReleasesInventoryThenCancelsOrderInReverseOrder() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING)).thenReturn(SagaMode.FORWARD);
        when(payments.charge(ORDER_ID, TOTAL, PaymentSimulationMode.REJECT))
                .thenThrow(new PaymentRejectedException(ORDER_ID));

        saga.process(ORDER_ID, PaymentSimulationMode.REJECT);

        InOrder flow = inOrder(processes, inventory, payments, orders);
        flow.verify(inventory).reserve(eq(ORDER_ID), any());
        flow.verify(payments).charge(ORDER_ID, TOTAL, PaymentSimulationMode.REJECT);
        flow.verify(processes).startCompensation(eq(ORDER_ID), anyString());
        flow.verify(processes).moveTo(ORDER_ID, SagaStep.RELEASE_INVENTORY);
        flow.verify(inventory).release(ORDER_ID);
        flow.verify(processes).moveTo(ORDER_ID, SagaStep.CANCEL_ORDER);
        flow.verify(orders).cancel(ORDER_ID);
        flow.verify(processes).markCompensated(ORDER_ID);

        verify(orders, never()).confirm(any());
        verify(processes, never()).markCompleted(any());
    }

    @Test
    void insufficientStockCancelsOrderWithoutChargingOrReleasing() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING)).thenReturn(SagaMode.FORWARD);
        when(inventory.reserve(eq(ORDER_ID), any())).thenThrow(new InsufficientStockException(PRODUCT_ID, 2, 1));

        saga.process(ORDER_ID, PaymentSimulationMode.APPROVE);

        InOrder flow = inOrder(processes, orders);
        flow.verify(processes).startCompensation(eq(ORDER_ID), anyString());
        flow.verify(orders).cancel(ORDER_ID);
        flow.verify(processes).markCompensated(ORDER_ID);

        verifyNoInteractions(payments);
        verify(inventory, never()).release(any());
        verify(orders, never()).confirm(any());
    }

    @Test
    void alreadyProcessedOrderHasNoSideEffects() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING))
                .thenThrow(new OrderAlreadyProcessedException(ORDER_ID, "process status is COMPLETED"));

        assertThrows(OrderAlreadyProcessedException.class, () -> saga.process(ORDER_ID, PaymentSimulationMode.APPROVE));

        verifyNoInteractions(inventory, payments, orders);
    }

    @Test
    void technicalFailureAfterApprovedPaymentMarksFailedAndDoesNotCompensate() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING)).thenReturn(SagaMode.FORWARD);
        doThrow(new IllegalStateException("database unavailable")).when(orders).confirm(ORDER_ID);

        assertThrows(SagaExecutionException.class, () -> saga.process(ORDER_ID, PaymentSimulationMode.APPROVE));

        verify(processes).markFailed(eq(ORDER_ID), anyString());
        verify(inventory, never()).release(any());
        verify(orders, never()).cancel(any());
    }

    @Test
    void resumingInterruptedCompensationOnlyRunsCompensations() {
        when(processes.begin(ORDER_ID, OrderStatus.PENDING)).thenReturn(SagaMode.COMPENSATION);

        saga.process(ORDER_ID, PaymentSimulationMode.APPROVE);

        InOrder flow = inOrder(inventory, orders, processes);
        flow.verify(inventory).release(ORDER_ID);
        flow.verify(orders).cancel(ORDER_ID);
        flow.verify(processes).markCompensated(ORDER_ID);
        verify(inventory, never()).reserve(any(), any());
        verifyNoInteractions(payments);
    }
}
