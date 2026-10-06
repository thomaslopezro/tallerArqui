package com.taller.ordersystem.order.domain;

import com.taller.ordersystem.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    @Test
    void totalUsesHistoricalUnitPrices() {
        Order order = new Order("customer-1", Instant.now());
        order.addItem(1L, 2, new BigDecimal("10.00"));
        order.addItem(2L, 1, new BigDecimal("5.50"));

        assertEquals(new BigDecimal("25.50"), order.totalAmount());
    }

    @Test
    void confirmAndCancelAreIdempotent() {
        Order confirmed = new Order("customer-1", Instant.now());
        assertTrue(confirmed.confirm(Instant.now()));
        assertFalse(confirmed.confirm(Instant.now()));
        assertThrows(InvalidStateException.class, () -> confirmed.cancel(Instant.now()));

        Order cancelled = new Order("customer-1", Instant.now());
        assertTrue(cancelled.cancel(Instant.now()));
        assertFalse(cancelled.cancel(Instant.now()));
        assertThrows(InvalidStateException.class, () -> cancelled.confirm(Instant.now()));
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }
}
