package com.taller.ordersystem.order.application.query;

import com.taller.ordersystem.order.domain.Order;
import com.taller.ordersystem.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Modelo de lectura de un pedido. totalAmount se calcula con los unitPrice historicos. */
public record OrderView(Long id, String customerId, OrderStatus status, Instant createdAt, Instant updatedAt,
                        BigDecimal totalAmount, List<OrderItemView> items) {

    public static OrderView from(Order order) {
        List<OrderItemView> items = order.getItems().stream()
                .map(i -> new OrderItemView(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.subtotal()))
                .toList();
        return new OrderView(order.getId(), order.getCustomerId(), order.getStatus(), order.getCreatedAt(),
                order.getUpdatedAt(), order.totalAmount(), items);
    }
}
