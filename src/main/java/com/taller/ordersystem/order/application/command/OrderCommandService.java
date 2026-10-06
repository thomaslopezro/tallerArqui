package com.taller.ordersystem.order.application.command;

import com.taller.ordersystem.inventory.application.query.ProductQueryService;
import com.taller.ordersystem.inventory.application.query.ProductView;
import com.taller.ordersystem.order.application.query.OrderView;
import com.taller.ordersystem.order.domain.Order;
import com.taller.ordersystem.order.domain.OrderNotFoundException;
import com.taller.ordersystem.order.persistence.OrderRepository;
import com.taller.ordersystem.shared.exception.InvalidRequestException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * CQRS - lado de comandos de pedidos.
 *
 * <p>{@code create} es una transaccion normal (REQUIRED). {@code confirm} y {@code cancel} son pasos
 * de la SAGA y usan {@code REQUIRES_NEW}: su commit no depende de ningun otro paso.</p>
 */
@ApplicationScoped
public class OrderCommandService {

    private static final Logger LOG = Logger.getLogger(OrderCommandService.class.getName());

    @Inject
    OrderRepository orders;

    @Inject
    ProductQueryService productQueries;

    /**
     * Crea el pedido en estado PENDING guardando el precio actual de cada producto como unitPrice.
     * NO ejecuta la SAGA (eso es POST /api/orders/{id}/process).
     */
    @Transactional
    public OrderView create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new InvalidRequestException("An order must contain at least one product");
        }
        Set<Long> seen = new HashSet<>();
        Order order = new Order(command.customerId(), Instant.now());
        for (CreateOrderCommand.Line line : command.items()) {
            if (!seen.add(line.productId())) {
                throw new InvalidRequestException("Product " + line.productId() + " appears more than once in the order");
            }
            ProductView product = productQueries.getById(line.productId());
            order.addItem(product.id(), line.quantity(), product.price());
        }
        orders.save(order);
        return OrderView.from(order);
    }

    /** Paso final de la SAGA. Idempotente: confirmar un pedido ya CONFIRMED no hace nada. */
    @Transactional(TxType.REQUIRES_NEW)
    public void confirm(Long orderId) {
        boolean changed = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId))
                .confirm(Instant.now());
        LOG.info(() -> "Order " + orderId + (changed ? " confirmed" : " already confirmed (idempotent)"));
    }

    /** Compensacion de la creacion del pedido. Idempotente: cancelar un pedido ya CANCELLED no hace nada. */
    @Transactional(TxType.REQUIRES_NEW)
    public void cancel(Long orderId) {
        boolean changed = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId))
                .cancel(Instant.now());
        LOG.info(() -> "Order " + orderId + (changed ? " cancelled" : " already cancelled (idempotent)"));
    }
}
