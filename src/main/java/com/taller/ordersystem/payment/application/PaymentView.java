package com.taller.ordersystem.payment.application;

import com.taller.ordersystem.payment.domain.Payment;
import com.taller.ordersystem.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentView(Long id, Long orderId, BigDecimal amount, PaymentStatus status, Instant createdAt) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus(),
                payment.getCreatedAt());
    }
}
