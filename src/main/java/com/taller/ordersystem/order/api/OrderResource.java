package com.taller.ordersystem.order.api;

import com.taller.ordersystem.order.application.command.CreateOrderCommand;
import com.taller.ordersystem.order.application.command.OrderCommandService;
import com.taller.ordersystem.order.application.query.OrderQueryService;
import com.taller.ordersystem.order.application.query.OrderView;
import com.taller.ordersystem.payment.domain.PaymentSimulationMode;
import com.taller.ordersystem.saga.OrderProcessQueryService;
import com.taller.ordersystem.saga.OrderProcessView;
import com.taller.ordersystem.saga.OrderSagaOrchestrator;
import com.taller.ordersystem.shared.api.RequestValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * API REST de pedidos. Crear un pedido y procesarlo son operaciones distintas:
 * POST /orders solo crea el pedido PENDING; POST /orders/{id}/process ejecuta la SAGA.
 */
@Path("orders")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderCommandService commands;

    @Inject
    OrderQueryService queries;

    @Inject
    OrderSagaOrchestrator saga;

    @Inject
    OrderProcessQueryService processQueries;

    @Inject
    RequestValidator validator;

    @POST
    public Response create(CreateOrderRequest request, @Context UriInfo uriInfo) {
        validator.validate(request);
        CreateOrderCommand command = new CreateOrderCommand(request.customerId(), request.items().stream()
                .map(item -> new CreateOrderCommand.Line(item.productId(), item.quantity()))
                .toList());
        OrderView created = commands.create(command);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.id())).build())
                .entity(created)
                .build();
    }

    @GET
    @Path("{id}")
    public OrderView findById(@PathParam("id") Long id) {
        return queries.getById(id);
    }

    /**
     * Ejecuta la SAGA del pedido.
     *
     * @param payment resultado del pago simulado: APPROVE (por defecto) o REJECT
     */
    @POST
    @Path("{id}/process")
    @Consumes(MediaType.WILDCARD)
    public OrderProcessView process(@PathParam("id") Long id, @QueryParam("payment") String payment) {
        return saga.process(id, PaymentSimulationMode.parse(payment));
    }

    @GET
    @Path("{id}/process")
    public OrderProcessView processStatus(@PathParam("id") Long id) {
        return processQueries.getByOrderId(id);
    }
}
