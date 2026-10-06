package com.taller.ordersystem.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reserva de inventario de un pedido. Guarda solo el orderId (sin relacion JPA con el modulo de pedidos).
 * order_id es UNIQUE: un pedido no puede tener dos reservas (idempotencia + defensa en BD).
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "reservation_id", nullable = false)
    private List<ReservationItem> items = new ArrayList<>();

    protected InventoryReservation() {
        // JPA
    }

    public InventoryReservation(Long orderId, Instant now) {
        this.orderId = orderId;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = now;
    }

    public void addItem(Long productId, int quantity) {
        items.add(new ReservationItem(productId, quantity));
    }

    /**
     * Marca la reserva como liberada. Devuelve false si ya estaba liberada (idempotente):
     * el llamador solo devuelve stock cuando esta transicion ocurre realmente.
     */
    public boolean release(Instant now) {
        if (status == ReservationStatus.RELEASED) {
            return false;
        }
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = now;
        return true;
    }

    public boolean isReleased() {
        return status == ReservationStatus.RELEASED;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
