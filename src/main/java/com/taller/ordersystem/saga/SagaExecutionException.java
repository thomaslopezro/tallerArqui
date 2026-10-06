package com.taller.ordersystem.saga;

/**
 * Error TECNICO durante la SAGA (no de negocio). El proceso queda FAILED y puede reintentarse;
 * se responde HTTP 500.
 */
public class SagaExecutionException extends RuntimeException {

    public SagaExecutionException(Long orderId, Throwable cause) {
        super("Technical failure while processing order " + orderId + ". The process was marked FAILED and can be "
                + "retried with POST /api/orders/" + orderId + "/process. Cause: " + cause.getMessage(), cause);
    }
}
