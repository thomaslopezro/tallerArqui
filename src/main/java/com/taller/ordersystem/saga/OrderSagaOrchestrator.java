package com.taller.ordersystem.saga;

import com.taller.ordersystem.inventory.application.command.InventoryCommandService;
import com.taller.ordersystem.inventory.application.command.ReservationLine;
import com.taller.ordersystem.order.application.command.OrderCommandService;
import com.taller.ordersystem.order.application.query.OrderQueryService;
import com.taller.ordersystem.order.application.query.OrderView;
import com.taller.ordersystem.payment.application.PaymentCommandService;
import com.taller.ordersystem.payment.domain.PaymentSimulationMode;
import com.taller.ordersystem.shared.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orquestador de la SAGA de procesamiento de pedidos.
 *
 * <p><b>NO es transaccional a proposito.</b> No existe una transaccion global: cada paso se ejecuta en
 * otro bean CDI con {@code @Transactional(REQUIRES_NEW)}, de modo que el interceptor JTA del contenedor
 * se aplica siempre (no hay self-invocation) y cada paso hace commit por separado.</p>
 *
 * <ul>
 *   <li>JTA = atomicidad dentro de cada paso.</li>
 *   <li>SAGA = consistencia del proceso completo mediante compensaciones en orden inverso.</li>
 * </ul>
 *
 * <pre>
 * Exito:            RESERVE_INVENTORY -> PROCESS_PAYMENT (APPROVED) -> CONFIRM_ORDER -> COMPLETED
 * Stock:            RESERVE_INVENTORY (falla, rollback local) -> CANCEL_ORDER -> COMPENSATED (sin pago)
 * Pago rechazado:   RESERVE_INVENTORY -> PROCESS_PAYMENT (REJECTED) -> RELEASE_INVENTORY -> CANCEL_ORDER -> COMPENSATED
 * Fallo tecnico:    FAILED (reintentable; todos los pasos son idempotentes)
 * </pre>
 */
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class OrderSagaOrchestrator {

    private static final Logger LOG = Logger.getLogger(OrderSagaOrchestrator.class.getName());

    @Inject
    OrderQueryService orderQueries;

    @Inject
    OrderProcessService processes;

    @Inject
    OrderProcessQueryService processQueries;

    @Inject
    InventoryCommandService inventory;

    @Inject
    PaymentCommandService payments;

    @Inject
    OrderCommandService orders;

    public OrderProcessView process(Long orderId, PaymentSimulationMode paymentMode) {
        // 1-2. El pedido existe (404) y, si no tiene proceso, esta PENDING (409)
        OrderView order = orderQueries.getById(orderId);
        // 3. Registrar/iniciar el OrderProcess (409 si ya fue procesado: idempotencia)
        SagaMode mode = processes.begin(orderId, order.status());
        LOG.info(() -> "Starting saga for order " + orderId + " in mode " + mode);
        try {
            if (mode == SagaMode.COMPENSATION) {
                compensate(orderId, fullCompensationPlan(), "Resuming compensation after a technical failure");
            } else {
                executeForward(order, paymentMode);
            }
        } catch (RuntimeException technicalFailure) {
            LOG.log(Level.SEVERE, "Technical failure in saga for order " + orderId, technicalFailure);
            markFailedSafely(orderId, technicalFailure);
            throw new SagaExecutionException(orderId, technicalFailure);
        }
        return processQueries.getByOrderId(orderId);
    }

    private void executeForward(OrderView order, PaymentSimulationMode paymentMode) {
        Long orderId = order.id();
        // Pila de compensaciones: se apilan a medida que los pasos tienen exito y se ejecutan en orden inverso (LIFO)
        Deque<SagaStep> compensations = new ArrayDeque<>();
        compensations.push(SagaStep.CANCEL_ORDER); // compensa la creacion del pedido
        try {
            // 4. Reservar inventario
            processes.moveTo(orderId, SagaStep.RESERVE_INVENTORY);
            inventory.reserve(orderId, reservationLines(order));
            compensations.push(SagaStep.RELEASE_INVENTORY);

            // 5. Procesar pago
            processes.moveTo(orderId, SagaStep.PROCESS_PAYMENT);
            payments.charge(orderId, order.totalAmount(), paymentMode);
        } catch (BusinessException businessFailure) {
            // InsufficientStock / ProductNotFound / PaymentRejected -> compensar
            LOG.info(() -> "Business failure in saga for order " + orderId + ": " + businessFailure.getMessage());
            compensate(orderId, compensations, businessFailure.getMessage());
            return;
        }

        // Punto de no retorno: el pago fue APROBADO. Si la confirmacion falla tecnicamente NO se compensa
        // automaticamente (el enunciado no define refund): el proceso queda FAILED y se reintenta.
        // 6. Confirmar pedido
        processes.moveTo(orderId, SagaStep.CONFIRM_ORDER);
        orders.confirm(orderId);
        // 7. Proceso COMPLETED
        processes.markCompleted(orderId);
        LOG.info(() -> "Saga completed for order " + orderId);
    }

    /** Ejecuta las compensaciones pendientes en orden inverso a los pasos que tuvieron exito. */
    private void compensate(Long orderId, Deque<SagaStep> compensations, String reason) {
        processes.startCompensation(orderId, reason);
        while (!compensations.isEmpty()) {
            SagaStep step = compensations.pop();
            processes.moveTo(orderId, step);
            switch (step) {
                case RELEASE_INVENTORY -> inventory.release(orderId);
                case CANCEL_ORDER -> orders.cancel(orderId);
                default -> throw new IllegalStateException("Step " + step + " is not a compensation");
            }
        }
        processes.markCompensated(orderId);
        LOG.info(() -> "Saga compensated for order " + orderId);
    }

    /** Al reanudar compensaciones se reejecutan todas: son idempotentes (release/cancel ya hechos no hacen nada). */
    private static Deque<SagaStep> fullCompensationPlan() {
        Deque<SagaStep> plan = new ArrayDeque<>();
        plan.push(SagaStep.CANCEL_ORDER);
        plan.push(SagaStep.RELEASE_INVENTORY);
        return plan;
    }

    private static List<ReservationLine> reservationLines(OrderView order) {
        return order.items().stream()
                .map(item -> new ReservationLine(item.productId(), item.quantity()))
                .toList();
    }

    private void markFailedSafely(Long orderId, RuntimeException cause) {
        try {
            processes.markFailed(orderId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not mark order process " + orderId + " as FAILED", e);
        }
    }
}
