package com.taller.ordersystem.saga;

import com.taller.ordersystem.inventory.application.query.InventoryQueryService;
import com.taller.ordersystem.inventory.application.query.ReservationView;
import com.taller.ordersystem.order.application.query.OrderQueryService;
import com.taller.ordersystem.order.application.query.OrderView;
import com.taller.ordersystem.payment.application.PaymentQueryService;
import com.taller.ordersystem.payment.application.PaymentView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** CQRS - consulta del estado de la SAGA, componiendo los query services de cada modulo. */
@ApplicationScoped
public class OrderProcessQueryService {

    @Inject
    OrderProcessRepository processes;

    @Inject
    OrderQueryService orderQueries;

    @Inject
    InventoryQueryService inventoryQueries;

    @Inject
    PaymentQueryService paymentQueries;

    public OrderProcessView getByOrderId(Long orderId) {
        OrderView order = orderQueries.getById(orderId);
        OrderProcess process = processes.findByOrderId(orderId)
                .orElseThrow(() -> new OrderProcessNotFoundException(orderId));
        ReservationView reservation = inventoryQueries.findReservationByOrderId(orderId).orElse(null);
        PaymentView payment = paymentQueries.findByOrderId(orderId).orElse(null);
        return new OrderProcessView(
                orderId,
                process.getStatus(),
                process.getCurrentStep(),
                process.getLastError(),
                order.status(),
                order.totalAmount(),
                reservation != null ? reservation.status() : null,
                payment != null ? payment.status() : null,
                payment != null ? payment.amount() : null,
                process.getCreatedAt(),
                process.getUpdatedAt());
    }
}
