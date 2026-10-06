package com.taller.ordersystem.order.domain;

import com.taller.ordersystem.shared.exception.InvalidRequestException;
import com.taller.ordersystem.shared.exception.InvalidStateException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pedido. Entidad JPA "CustomerOrder" (ORDER es palabra reservada en JPQL/SQL), tabla "orders".
 *
 * <p>Las transiciones son idempotentes: confirmar un pedido CONFIRMED o cancelar uno CANCELLED
 * no hace nada y devuelve false. Las transiciones imposibles (confirmar uno cancelado...) lanzan
 * {@link InvalidStateException}.</p>
 */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA
    }

    public Order(String customerId, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidRequestException("customerId is required");
        }
        this.customerId = customerId.trim();
        this.status = OrderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(Long productId, int quantity, BigDecimal unitPrice) {
        if (status != OrderStatus.PENDING) {
            throw new InvalidStateException("Items can only be added to PENDING orders");
        }
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new InvalidRequestException("Unit price must be greater than 0");
        }
        items.add(new OrderItem(productId, quantity, unitPrice));
    }

    /** Total calculado con los precios historicos (unitPrice) guardados en el pedido. */
    public BigDecimal totalAmount() {
        return items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** @return true si el pedido cambio de estado; false si ya estaba CONFIRMED (idempotente). */
    public boolean confirm(Instant now) {
        if (status == OrderStatus.CONFIRMED) {
            return false;
        }
        if (status != OrderStatus.PENDING) {
            throw new InvalidStateException("Order " + id + " cannot be confirmed from status " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = now;
        return true;
    }

    /** @return true si el pedido cambio de estado; false si ya estaba CANCELLED (idempotente). */
    public boolean cancel(Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        if (status != OrderStatus.PENDING) {
            throw new InvalidStateException("Order " + id + " cannot be cancelled from status " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
