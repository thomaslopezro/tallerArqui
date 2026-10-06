package com.taller.ordersystem.saga;

/** Como debe continuar el orquestador al iniciar o reanudar un proceso. */
public enum SagaMode {
    /** Ejecutar (o reintentar) los pasos hacia adelante. */
    FORWARD,
    /** Reanudar compensaciones interrumpidas por un fallo tecnico. */
    COMPENSATION
}
