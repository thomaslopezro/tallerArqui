package com.taller.ordersystem.shared.exception;

/** Transicion de estado no permitida por el dominio (HTTP 409). */
public class InvalidStateException extends BusinessException {

    public InvalidStateException(String message) {
        super(ErrorType.CONFLICT, "INVALID_STATE", message);
    }
}
