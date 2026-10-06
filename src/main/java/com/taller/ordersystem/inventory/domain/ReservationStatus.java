package com.taller.ordersystem.inventory.domain;

public enum ReservationStatus {
    /** Stock descontado para el pedido. Si el pedido se confirma, la reserva queda consumida en este estado. */
    RESERVED,
    /** Stock devuelto por compensacion. Estado final. */
    RELEASED
}
