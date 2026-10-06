package com.taller.ordersystem.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateStockRequest(@NotNull @PositiveOrZero Integer availableStock) {
}
