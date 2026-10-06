package com.taller.ordersystem.saga;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

/** Se intento procesar un pedido cuyo proceso ya termino o esta en curso (HTTP 409). */
public class OrderAlreadyProcessedException extends BusinessException {

    public OrderAlreadyProcessedException(Long orderId, String detail) {
        super(ErrorType.CONFLICT, "ORDER_ALREADY_PROCESSED",
                "Order " + orderId + " was already processed: " + detail + ". No action was taken");
    }
}
