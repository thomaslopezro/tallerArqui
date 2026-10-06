package com.taller.ordersystem.saga;

import com.taller.ordersystem.order.domain.Order;
import com.taller.ordersystem.order.domain.OrderStatus;
import com.taller.ordersystem.order.persistence.OrderRepository;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderProcessRepository processes;
    @InjectMocks OrderProcessService service;

    @Test
    void databaseFailureIsNotDisguisedAsDuplicateRequest() {
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(new Order("c", Instant.now())));
        when(processes.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        PersistenceException failure = new PersistenceException("database unavailable");
        when(processes.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(PersistenceException.class,
                () -> service.begin(1L, OrderStatus.PENDING)));
    }

    @Test
    void existingProcessCannotBeStartedAgain() {
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(new Order("c", Instant.now())));
        when(processes.findByOrderIdForUpdate(1L))
                .thenReturn(Optional.of(new OrderProcess(1L, Instant.now())));
        assertThrows(OrderAlreadyProcessedException.class, () -> service.begin(1L, OrderStatus.PENDING));
        verify(processes, never()).saveAndFlush(any());
    }

    @Test
    void stalePendingSnapshotCannotStartCancelledOrder() {
        Order cancelled = new Order("c", Instant.now());
        cancelled.cancel(Instant.now());
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(cancelled));
        when(processes.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(com.taller.ordersystem.shared.exception.InvalidStateException.class,
                () -> service.begin(1L, OrderStatus.PENDING));
        verify(processes, never()).saveAndFlush(any());
    }
}
