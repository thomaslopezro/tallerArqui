package com.taller.ordersystem.order.persistence;

import com.taller.ordersystem.order.domain.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class OrderRepository {

    @PersistenceContext
    EntityManager em;

    public Order save(Order order) {
        em.persist(order);
        return order;
    }

    /** Carga el pedido con sus lineas en una sola consulta (sirve tambien fuera de transaccion). */
    public Optional<Order> findWithItems(Long orderId) {
        return em.createQuery(
                        "SELECT DISTINCT o FROM CustomerOrder o LEFT JOIN FETCH o.items WHERE o.id = :id", Order.class)
                .setParameter("id", orderId)
                .getResultStream()
                .findFirst();
    }

    /** Bloquea la fila del pedido: confirmaciones/cancelaciones concurrentes se serializan. */
    public Optional<Order> findByIdForUpdate(Long orderId) {
        return Optional.ofNullable(em.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE));
    }
}
