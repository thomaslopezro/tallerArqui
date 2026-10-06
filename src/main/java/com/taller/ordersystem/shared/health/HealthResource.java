package com.taller.ordersystem.shared.health;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** GET /api/health: usado por el healthcheck de Docker Compose. */
@Path("health")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    DatabaseHealthService databaseHealth;

    @GET
    public Response health() {
        boolean dbUp = databaseHealth.isDatabaseUp();
        String status = dbUp ? "UP" : "DOWN";
        return Response.status(dbUp ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(new HealthView(status, status))
                .build();
    }

    public record HealthView(String status, String database) {
    }
}
