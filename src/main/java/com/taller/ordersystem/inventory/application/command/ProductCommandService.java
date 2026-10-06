package com.taller.ordersystem.inventory.application.command;

import com.taller.ordersystem.inventory.application.query.ProductView;
import com.taller.ordersystem.inventory.domain.Product;
import com.taller.ordersystem.inventory.domain.ProductInUseException;
import com.taller.ordersystem.inventory.domain.ProductNotFoundException;
import com.taller.ordersystem.inventory.persistence.InventoryReservationRepository;
import com.taller.ordersystem.inventory.persistence.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

/**
 * CQRS - lado de comandos de productos. Cada metodo es una transaccion JTA (REQUIRED).
 * Los cambios de stock bloquean la fila para no pisar una reserva concurrente.
 */
@ApplicationScoped
@Transactional
public class ProductCommandService {

    @Inject
    ProductRepository products;

    @Inject
    InventoryReservationRepository reservations;

    public ProductView create(String name, BigDecimal price, int availableStock) {
        return ProductView.from(products.save(new Product(name, price, availableStock)));
    }

    public ProductView changePrice(Long productId, BigDecimal newPrice) {
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        product.changePrice(newPrice);
        return ProductView.from(product);
    }

    public ProductView changeStock(Long productId, int newStock) {
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        product.changeStock(newStock);
        return ProductView.from(product);
    }

    public void delete(Long productId) {
        Product product = products.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (reservations.existsItemForProduct(productId)) {
            throw new ProductInUseException(productId);
        }
        products.delete(product);
    }
}
