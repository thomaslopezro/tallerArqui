package com.taller.ordersystem.inventory.application.query;

import com.taller.ordersystem.inventory.domain.Product;

import java.math.BigDecimal;

/** Modelo de lectura de un producto. */
public record ProductView(Long id, String name, BigDecimal price, int availableStock) {

    public static ProductView from(Product product) {
        return new ProductView(product.getId(), product.getName(), product.getPrice(), product.getAvailableStock());
    }
}
