package com.taller.ordersystem.payment.application;

import com.taller.ordersystem.payment.domain.Payment;
import com.taller.ordersystem.payment.domain.PaymentRejectedException;
import com.taller.ordersystem.payment.domain.PaymentSimulationMode;
import com.taller.ordersystem.payment.persistence.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

/** Paso de la SAGA: cobrar el pedido. */
@ApplicationScoped
public class PaymentCommandService {

    private static final Logger LOG = Logger.getLogger(PaymentCommandService.class.getName());

    @Inject
    PaymentRepository payments;

    @Inject
    SimulatedPaymentGateway gateway;

    /**
     * Cobra el pedido en su propia transaccion (REQUIRES_NEW).
     *
     * <p>Idempotente: si el pedido ya tiene un pago registrado NO se vuelve a cobrar; se devuelve
     * el resultado existente.</p>
     *
     * <p>{@code dontRollbackOn = PaymentRejectedException.class}: un rechazo es un resultado de negocio
     * que debe quedar registrado (payment = REJECTED). Por eso la excepcion NO revierte la transaccion;
     * solo avisa al orquestador para que compense.</p>
     *
     * @throws PaymentRejectedException si el pago (nuevo o ya existente) esta rechazado
     */
    @Transactional(value = TxType.REQUIRES_NEW, dontRollbackOn = PaymentRejectedException.class)
    public PaymentView charge(Long orderId, BigDecimal amount, PaymentSimulationMode mode) {
        Optional<Payment> existing = payments.findByOrderId(orderId);
        Payment payment;
        if (existing.isPresent()) {
            payment = existing.get();
            LOG.info(() -> "Order " + orderId + " already has a payment (" + existing.get().getStatus()
                    + ") - not charging again (idempotent)");
        } else {
            payment = payments.save(new Payment(orderId, amount, gateway.charge(orderId, amount, mode), Instant.now()));
        }
        if (payment.isRejected()) {
            throw new PaymentRejectedException(orderId);
        }
        return PaymentView.from(payment);
    }
}
