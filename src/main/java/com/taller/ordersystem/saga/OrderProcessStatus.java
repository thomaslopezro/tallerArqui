package com.taller.ordersystem.saga;

public enum OrderProcessStatus {
    /** Ejecutando los pasos hacia adelante. */
    RUNNING,
    /** Proceso terminado con exito: pedido CONFIRMED. */
    COMPLETED,
    /** Fallo de negocio: ejecutando compensaciones. */
    COMPENSATING,
    /** Compensaciones terminadas: pedido CANCELLED y stock devuelto. */
    COMPENSATED,
    /** Fallo tecnico inesperado: el proceso se puede reintentar (todos los pasos son idempotentes). */
    FAILED
}
