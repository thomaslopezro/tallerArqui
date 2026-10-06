package com.taller.ordersystem.inventory.application.query;

import com.taller.ordersystem.inventory.domain.ProductNotFoundException;
import com.taller.ordersystem.inventory.persistence.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/** CQRS - lado de consulta de productos: solo lectura, devuelve DTOs. */
@ApplicationScoped
public class ProductQueryService {

    @Inject
    ProductRepository products;

    public List<ProductView> findAll() {
        return products.findAll().stream().map(ProductView::from).toList();
    }

    public ProductView getById(Long productId) {
        return products.findById(productId)
                .map(ProductView::from)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
