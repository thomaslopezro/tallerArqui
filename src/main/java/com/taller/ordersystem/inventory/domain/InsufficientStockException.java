package com.taller.ordersystem.inventory.domain;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(ErrorType.CONFLICT, "INSUFFICIENT_STOCK",
                "Insufficient stock for product " + productId + ": requested " + requested + ", available " + available);
    }
}
