package com.taller.ordersystem.payment.domain;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

/**
 * El pago fue rechazado. Es un resultado de negocio: el registro REJECTED se confirma (commit)
 * y el orquestador ejecuta las compensaciones.
 */
public class PaymentRejectedException extends BusinessException {

    public PaymentRejectedException(Long orderId) {
        super(ErrorType.CONFLICT, "PAYMENT_REJECTED", "Payment for order " + orderId + " was rejected");
    }
}
