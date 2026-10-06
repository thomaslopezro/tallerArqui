package com.taller.ordersystem.inventory.persistence;

import com.taller.ordersystem.inventory.domain.InventoryReservation;
import com.taller.ordersystem.inventory.domain.ReservationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class InventoryReservationRepository {

    @PersistenceContext
    EntityManager em;

    public InventoryReservation save(InventoryReservation reservation) {
        em.persist(reservation);
        return reservation;
    }

    public Optional<InventoryReservation> findByOrderId(Long orderId) {
        return em.createQuery(
                        "SELECT DISTINCT r FROM InventoryReservation r LEFT JOIN FETCH r.items WHERE r.orderId = :orderId",
                        InventoryReservation.class)
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst();
    }

    /** Bloquea la fila de la reserva para que dos liberaciones concurrentes no devuelvan stock dos veces. */
    public Optional<InventoryReservation> findByOrderIdForUpdate(Long orderId) {
        return em.createQuery("SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId", InventoryReservation.class)
                .setParameter("orderId", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }

    public boolean existsItemForProduct(Long productId) {
        Long count = em.createQuery("SELECT COUNT(i) FROM ReservationItem i WHERE i.productId = :productId", Long.class)
                .setParameter("productId", productId)
                .getSingleResult();
        return count > 0;
    }

    /** Unidades del producto en reservas activas (RESERVED). */
    public long sumReservedQuantity(Long productId) {
        Long sum = em.createQuery("""
                        SELECT SUM(i.quantity) FROM InventoryReservation r JOIN r.items i
                        WHERE i.productId = :productId AND r.status = :status
                        """, Long.class)
                .setParameter("productId", productId)
                .setParameter("status", ReservationStatus.RESERVED)
                .getSingleResult();
        return sum == null ? 0 : sum;
    }
}
