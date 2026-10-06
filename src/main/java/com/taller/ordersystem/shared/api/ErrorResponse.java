package com.taller.ordersystem.shared.api;

import java.time.Instant;
import java.util.List;

/** Formato JSON unico para todas las respuestas de error de la API. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details) {
}
