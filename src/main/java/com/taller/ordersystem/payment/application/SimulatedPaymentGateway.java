package com.taller.ordersystem.payment.application;

import com.taller.ordersystem.payment.domain.PaymentSimulationMode;
import com.taller.ordersystem.payment.domain.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Pasarela de pago SIMULADA (no hay proveedor real). El resultado lo decide de forma
 * determinista la instruccion recibida, para que las demostraciones y pruebas sean reproducibles.
 */
@ApplicationScoped
public class SimulatedPaymentGateway {

    private static final Logger LOG = Logger.getLogger(SimulatedPaymentGateway.class.getName());

    public PaymentStatus charge(Long orderId, BigDecimal amount, PaymentSimulationMode mode) {
        PaymentStatus result = mode == PaymentSimulationMode.REJECT ? PaymentStatus.REJECTED : PaymentStatus.APPROVED;
        LOG.info(() -> "Simulated charge of " + amount + " for order " + orderId + " -> " + result);
        return result;
    }
}
