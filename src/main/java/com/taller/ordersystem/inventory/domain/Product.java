package com.taller.ordersystem.inventory.domain;

import com.taller.ordersystem.shared.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Producto del catalogo con su stock disponible. Las reglas de stock viven aqui
 * (no en los servicios) para que no se puedan saltar.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    protected Product() {
        // JPA
    }

    public Product(String name, BigDecimal price, int availableStock) {
        rename(name);
        changePrice(price);
        changeStock(availableStock);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new InvalidRequestException("Product name is required");
        }
        this.name = newName.trim();
    }

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new InvalidRequestException("Product price must be greater than 0");
        }
        this.price = newPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public void changeStock(int newStock) {
        if (newStock < 0) {
            throw new InvalidRequestException("Product stock must be greater than or equal to 0");
        }
        this.availableStock = newStock;
    }

    /** Descuenta stock para una reserva. Nunca deja el stock en negativo. */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > availableStock) {
            throw new InsufficientStockException(id, quantity, availableStock);
        }
        this.availableStock -= quantity;
    }

    /** Devuelve al stock una cantidad previamente reservada (compensacion). */
    public void release(int quantity) {
        requirePositive(quantity);
        this.availableStock += quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than 0");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getAvailableStock() {
        return availableStock;
    }
}
