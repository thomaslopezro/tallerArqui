package com.taller.ordersystem.order.domain;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(Long orderId) {
        super(ErrorType.NOT_FOUND, "ORDER_NOT_FOUND", "Order " + orderId + " not found");
    }
}
