package com.taller.ordersystem.inventory.application.query;

import com.taller.ordersystem.inventory.domain.InventoryReservation;
import com.taller.ordersystem.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationView(Long id, Long orderId, ReservationStatus status, Instant createdAt,
                              Instant releasedAt, List<Item> items) {

    public record Item(Long productId, int quantity) {
    }

    public static ReservationView from(InventoryReservation reservation) {
        List<Item> items = reservation.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity()))
                .toList();
        return new ReservationView(reservation.getId(), reservation.getOrderId(), reservation.getStatus(),
                reservation.getCreatedAt(), reservation.getReleasedAt(), items);
    }
}
