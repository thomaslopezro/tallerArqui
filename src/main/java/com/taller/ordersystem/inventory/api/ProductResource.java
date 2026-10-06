package com.taller.ordersystem.inventory.api;

import com.taller.ordersystem.inventory.application.command.ProductCommandService;
import com.taller.ordersystem.inventory.application.query.InventoryQueryService;
import com.taller.ordersystem.inventory.application.query.ProductInventoryView;
import com.taller.ordersystem.inventory.application.query.ProductQueryService;
import com.taller.ordersystem.inventory.application.query.ProductView;
import com.taller.ordersystem.shared.api.RequestValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

import java.util.List;

/**
 * API REST de productos. Solo adapta HTTP: valida la entrada y delega en el lado de comandos
 * o en el lado de consultas (CQRS). No contiene logica de negocio ni usa EntityManager.
 */
@Path("products")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @Inject
    ProductCommandService commands;

    @Inject
    ProductQueryService queries;

    @Inject
    InventoryQueryService inventoryQueries;

    @Inject
    RequestValidator validator;

    @POST
    public Response create(CreateProductRequest request, @Context UriInfo uriInfo) {
        validator.validate(request);
        ProductView created = commands.create(request.name(), request.price(), request.availableStock());
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.id())).build())
                .entity(created)
                .build();
    }

    @GET
    public List<ProductView> findAll() {
        return queries.findAll();
    }

    @GET
    @Path("{id}")
    public ProductView findById(@PathParam("id") Long id) {
        return queries.getById(id);
    }

    @PUT
    @Path("{id}/price")
    public ProductView changePrice(@PathParam("id") Long id, UpdatePriceRequest request) {
        validator.validate(request);
        return commands.changePrice(id, request.price());
    }

    @PUT
    @Path("{id}/stock")
    public ProductView changeStock(@PathParam("id") Long id, UpdateStockRequest request) {
        validator.validate(request);
        return commands.changeStock(id, request.availableStock());
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") Long id) {
        commands.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("{id}/inventory")
    public ProductInventoryView inventory(@PathParam("id") Long id) {
        return inventoryQueries.getProductInventory(id);
    }
}
