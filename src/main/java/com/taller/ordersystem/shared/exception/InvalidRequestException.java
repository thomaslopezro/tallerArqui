package com.taller.ordersystem.shared.exception;

import java.util.List;

/** Datos de entrada invalidos (HTTP 400). */
public class InvalidRequestException extends BusinessException {

    private final List<String> details;

    public InvalidRequestException(String message) {
        this(message, List.of());
    }

    public InvalidRequestException(String message, List<String> details) {
        super(ErrorType.INVALID_REQUEST, "INVALID_REQUEST", message);
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
