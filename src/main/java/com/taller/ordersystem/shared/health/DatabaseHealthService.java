package com.taller.ordersystem.shared.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Comprueba que el datasource administrado por WildFly responde. */
@ApplicationScoped
public class DatabaseHealthService {

    private static final Logger LOG = Logger.getLogger(DatabaseHealthService.class.getName());

    @PersistenceContext
    EntityManager em;

    public boolean isDatabaseUp() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Database health check failed", e);
            return false;
        }
    }
}
