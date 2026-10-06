package com.taller.ordersystem.inventory.domain;

import com.taller.ordersystem.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDomainTest {

    @Test
    void reserveNeverLeavesNegativeStock() {
        Product product = new Product("Keyboard", new BigDecimal("10.00"), 1);

        assertThrows(InsufficientStockException.class, () -> product.reserve(2));
        assertEquals(1, product.getAvailableStock());

        product.reserve(1);
        assertEquals(0, product.getAvailableStock());
    }

    @Test
    void productRejectsInvalidData() {
        assertThrows(InvalidRequestException.class, () -> new Product(" ", BigDecimal.TEN, 1));
        assertThrows(InvalidRequestException.class, () -> new Product("Mouse", BigDecimal.ZERO, 1));
        assertThrows(InvalidRequestException.class, () -> new Product("Mouse", BigDecimal.TEN, -1));
    }

    @Test
    void reservationIsReleasedOnlyOnce() {
        InventoryReservation reservation = new InventoryReservation(1L, Instant.now());

        assertTrue(reservation.release(Instant.now()));
        assertFalse(reservation.release(Instant.now()), "second release must be a no-op");
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
    }
}
