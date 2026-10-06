package com.taller.ordersystem.shared.exception;

/**
 * Categoria de un error de negocio. La capa REST la traduce a un codigo HTTP;
 * el dominio no conoce HTTP.
 */
public enum ErrorType {
    /** Peticion invalida (datos de entrada incorrectos). */
    INVALID_REQUEST,
    /** El recurso solicitado no existe. */
    NOT_FOUND,
    /** Conflicto con el estado actual (stock, estado del pedido, pedido ya procesado...). */
    CONFLICT
}
