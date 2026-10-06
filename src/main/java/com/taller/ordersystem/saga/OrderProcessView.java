package com.taller.ordersystem.saga;

import com.taller.ordersystem.inventory.domain.ReservationStatus;
import com.taller.ordersystem.order.domain.OrderStatus;
import com.taller.ordersystem.payment.domain.PaymentStatus;

import jakarta.json.bind.annotation.JsonbNillable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vista consolidada (solo lectura) del resultado de la SAGA de un pedido: estado del proceso,
 * del pedido, de la reserva y del pago. Pensada para demostrar cada escenario con una sola consulta.
 * reservationStatus / paymentStatus son null si ese paso nunca llego a registrarse.
 */
@JsonbNillable
public record OrderProcessView(
        Long orderId,
        OrderProcessStatus processStatus,
        SagaStep currentStep,
        String lastError,
        OrderStatus orderStatus,
        BigDecimal orderTotal,
        ReservationStatus reservationStatus,
        PaymentStatus paymentStatus,
        BigDecimal paymentAmount,
        Instant createdAt,
        Instant updatedAt) {
}
