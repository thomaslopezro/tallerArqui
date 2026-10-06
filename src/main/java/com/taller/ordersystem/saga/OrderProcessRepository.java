package com.taller.ordersystem.saga;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class OrderProcessRepository {

    @PersistenceContext
    EntityManager em;

    /** Inserta y fuerza el INSERT inmediato para detectar aqui la violacion de UNIQUE(order_id). */
    public OrderProcess saveAndFlush(OrderProcess process) {
        em.persist(process);
        em.flush();
        return process;
    }

    public Optional<OrderProcess> findByOrderId(Long orderId) {
        return em.createQuery("SELECT p FROM OrderProcess p WHERE p.orderId = :orderId", OrderProcess.class)
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst();
    }

    public Optional<OrderProcess> findByOrderIdForUpdate(Long orderId) {
        return em.createQuery("SELECT p FROM OrderProcess p WHERE p.orderId = :orderId", OrderProcess.class)
                .setParameter("orderId", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }
}
