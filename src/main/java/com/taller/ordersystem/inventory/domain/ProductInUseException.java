package com.taller.ordersystem.inventory.domain;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.ErrorType;

/** No se borra un producto referenciado por reservas: se perderia la trazabilidad y no se podria liberar stock. */
public class ProductInUseException extends BusinessException {

    public ProductInUseException(Long productId) {
        super(ErrorType.CONFLICT, "PRODUCT_IN_USE",
                "Product " + productId + " cannot be deleted because it is referenced by inventory reservations");
    }
}
