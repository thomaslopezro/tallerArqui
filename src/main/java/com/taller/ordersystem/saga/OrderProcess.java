package com.taller.ordersystem.saga;

import com.taller.ordersystem.shared.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Estado persistente de la SAGA de un pedido. Permite saber en que paso esta cada proceso,
 * por que fallo y reanudarlo. order_id es UNIQUE: un pedido tiene como maximo un proceso,
 * lo que impide ejecutar la SAGA dos veces (idempotencia de processOrder).
 */
@Entity
@Table(name = "order_processes")
public class OrderProcess {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderProcessStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private SagaStep currentStep;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderProcess() {
        // JPA
    }

    public OrderProcess(Long orderId, Instant now) {
        this.orderId = orderId;
        this.status = OrderProcessStatus.RUNNING;
        this.currentStep = SagaStep.STARTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void moveTo(SagaStep step, Instant now) {
        requireStatus(step.isCompensation() ? OrderProcessStatus.COMPENSATING : OrderProcessStatus.RUNNING);
        this.currentStep = step;
        this.updatedAt = now;
    }

    public void startCompensation(String reason, Instant now) {
        if (status != OrderProcessStatus.RUNNING && status != OrderProcessStatus.COMPENSATING) {
            throw new InvalidStateException("Cannot compensate order process in status " + status);
        }
        this.status = OrderProcessStatus.COMPENSATING;
        // Persistir la direccion junto al estado, antes de ejecutar cualquier compensacion.
        this.currentStep = SagaStep.RELEASE_INVENTORY;
        this.lastError = truncate(reason);
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        requireStatus(OrderProcessStatus.RUNNING);
        this.status = OrderProcessStatus.COMPLETED;
        this.currentStep = SagaStep.DONE;
        this.updatedAt = now;
    }

    public void markCompensated(Instant now) {
        requireStatus(OrderProcessStatus.COMPENSATING);
        this.status = OrderProcessStatus.COMPENSATED;
        this.currentStep = SagaStep.DONE;
        this.updatedAt = now;
    }

    /** Fallo tecnico: se conserva currentStep para saber donde reanudar. */
    public void fail(String error, Instant now) {
        this.status = OrderProcessStatus.FAILED;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    /** Reanuda un proceso FAILED: continua compensando si fallo durante una compensacion. */
    public SagaMode resume(Instant now) {
        requireStatus(OrderProcessStatus.FAILED);
        this.updatedAt = now;
        if (currentStep.isCompensation()) {
            this.status = OrderProcessStatus.COMPENSATING;
            return SagaMode.COMPENSATION;
        }
        this.status = OrderProcessStatus.RUNNING;
        return SagaMode.FORWARD;
    }

    private void requireStatus(OrderProcessStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Order process for order " + orderId + " is " + status + " (expected " + expected + ")");
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderProcessStatus getStatus() {
        return status;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
