package com.taller.ordersystem.order.application.query;

import java.math.BigDecimal;

public record OrderItemView(Long id, Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
}
