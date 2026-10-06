package com.taller.ordersystem.payment.persistence;

import com.taller.ordersystem.payment.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class PaymentRepository {

    @PersistenceContext
    EntityManager em;

    public Payment save(Payment payment) {
        em.persist(payment);
        return payment;
    }

    public Optional<Payment> findByOrderId(Long orderId) {
        return em.createQuery("SELECT p FROM Payment p WHERE p.orderId = :orderId", Payment.class)
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst();
    }
}
