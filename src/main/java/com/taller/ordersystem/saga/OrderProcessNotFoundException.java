package com.taller.ordersystem.saga;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

public class OrderProcessNotFoundException extends BusinessException {

    public OrderProcessNotFoundException(Long orderId) {
        super(ErrorType.NOT_FOUND, "ORDER_PROCESS_NOT_FOUND",
                "Order " + orderId + " has not been processed yet (POST /api/orders/" + orderId + "/process)");
    }
}
