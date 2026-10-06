package com.taller.ordersystem.saga;

/** Paso que la SAGA esta ejecutando (o ejecuto por ultima vez). */
public enum SagaStep {
    STARTED(false),
    RESERVE_INVENTORY(false),
    PROCESS_PAYMENT(false),
    CONFIRM_ORDER(false),
    /** Compensacion de RESERVE_INVENTORY. */
    RELEASE_INVENTORY(true),
    /** Compensacion de la creacion del pedido. */
    CANCEL_ORDER(true),
    DONE(false);

    private final boolean compensation;

    SagaStep(boolean compensation) {
        this.compensation = compensation;
    }

    public boolean isCompensation() {
        return compensation;
    }
}
