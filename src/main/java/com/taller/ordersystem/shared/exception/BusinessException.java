package com.taller.ordersystem.shared.exception;

/**
 * Base de todos los errores de negocio (esperados). Son RuntimeException, por lo que
 * {@code @Transactional} hace rollback por defecto cuando atraviesan un limite transaccional.
 * Los errores tecnicos (inesperados) NO extienden de esta clase.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorType type;
    private final String code;

    protected BusinessException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }
}
