package com.taller.ordersystem.inventory.application.query;

/**
 * Vista de inventario de un producto.
 *
 * @param availableStock   unidades que aun se pueden vender
 * @param reservedQuantity unidades comprometidas en reservas RESERVED (pedidos en curso o confirmados)
 */
public record ProductInventoryView(Long productId, String productName, int availableStock, long reservedQuantity) {
}
