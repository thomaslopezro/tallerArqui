package com.taller.ordersystem.inventory.application.query;

import com.taller.ordersystem.inventory.domain.Product;
import com.taller.ordersystem.inventory.domain.ProductNotFoundException;
import com.taller.ordersystem.inventory.persistence.InventoryReservationRepository;
import com.taller.ordersystem.inventory.persistence.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/** CQRS - lado de consulta del inventario y de las reservas. */
@ApplicationScoped
public class InventoryQueryService {

    @Inject
    ProductRepository products;

    @Inject
    InventoryReservationRepository reservations;

    public ProductInventoryView getProductInventory(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        return new ProductInventoryView(product.getId(), product.getName(), product.getAvailableStock(),
                reservations.sumReservedQuantity(productId));
    }

    public Optional<ReservationView> findReservationByOrderId(Long orderId) {
        return reservations.findByOrderId(orderId).map(ReservationView::from);
    }
}
