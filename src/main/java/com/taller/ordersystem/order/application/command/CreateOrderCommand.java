package com.taller.ordersystem.order.application.command;

import java.util.List;

public record CreateOrderCommand(String customerId, List<Line> items) {

    public record Line(Long productId, int quantity) {
    }
}
