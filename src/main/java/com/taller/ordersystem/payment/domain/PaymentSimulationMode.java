package com.taller.ordersystem.payment.domain;

import com.taller.ordersystem.shared.exception.InvalidRequestException;

import java.util.Locale;

/**
 * Instruccion determinista para la pasarela de pago simulada. Se indica en
 * {@code POST /api/orders/{id}/process?payment=APPROVE|REJECT} (por defecto APPROVE).
 * No hay aleatoriedad: el mismo input produce siempre el mismo resultado.
 */
public enum PaymentSimulationMode {
    APPROVE,
    REJECT;

    /** Acepta APPROVE/APPROVED/REJECT/REJECTED sin distinguir mayusculas; null o vacio = APPROVE. */
    public static PaymentSimulationMode parse(String value) {
        if (value == null || value.isBlank()) {
            return APPROVE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> APPROVE;
            case "REJECT", "REJECTED" -> REJECT;
            default -> throw new InvalidRequestException(
                    "Invalid payment simulation value '" + value + "'. Use APPROVE or REJECT");
        };
    }
}
