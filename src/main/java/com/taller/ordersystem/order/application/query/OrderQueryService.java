package com.taller.ordersystem.order.application.query;

import com.taller.ordersystem.order.domain.OrderNotFoundException;
import com.taller.ordersystem.order.persistence.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** CQRS - lado de consulta de pedidos. */
@ApplicationScoped
public class OrderQueryService {

    @Inject
    OrderRepository orders;

    public OrderView getById(Long orderId) {
        return orders.findWithItems(orderId)
                .map(OrderView::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
