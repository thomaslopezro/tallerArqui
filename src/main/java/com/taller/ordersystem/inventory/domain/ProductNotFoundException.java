package com.taller.ordersystem.inventory.domain;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(Long productId) {
        super(ErrorType.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product " + productId + " not found");
    }
}
