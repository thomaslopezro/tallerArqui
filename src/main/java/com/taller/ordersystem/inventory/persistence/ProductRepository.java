package com.taller.ordersystem.inventory.persistence;

import com.taller.ordersystem.inventory.domain.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductRepository {

    @PersistenceContext
    EntityManager em;

    public Product save(Product product) {
        em.persist(product);
        return product;
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(em.find(Product.class, id));
    }

    /**
     * Lee el producto con bloqueo pesimista de escritura (SELECT ... FOR UPDATE en PostgreSQL).
     * Otra transaccion que intente bloquear la misma fila espera hasta el commit/rollback de esta,
     * por lo que dos reservas concurrentes no pueden ver el mismo stock (evita overselling).
     * Debe llamarse dentro de una transaccion JTA activa.
     */
    public Optional<Product> findByIdForUpdate(Long id) {
        return Optional.ofNullable(em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public List<Product> findAll() {
        return em.createQuery("SELECT p FROM Product p ORDER BY p.id", Product.class).getResultList();
    }

    public void delete(Product product) {
        em.remove(product);
    }
}
