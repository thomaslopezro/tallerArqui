package com.taller.ordersystem.payment.application;

import com.taller.ordersystem.payment.persistence.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/** CQRS - lado de consulta de pagos. */
@ApplicationScoped
public class PaymentQueryService {

    @Inject
    PaymentRepository payments;

    public Optional<PaymentView> findByOrderId(Long orderId) {
        return payments.findByOrderId(orderId).map(PaymentView::from);
    }
}
