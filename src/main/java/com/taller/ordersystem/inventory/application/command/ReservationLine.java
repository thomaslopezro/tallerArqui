package com.taller.ordersystem.inventory.application.command;

/** Producto y cantidad a reservar para un pedido. */
public record ReservationLine(Long productId, int quantity) {
}
