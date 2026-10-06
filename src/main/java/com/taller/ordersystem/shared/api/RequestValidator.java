package com.taller.ordersystem.shared.api;

import com.taller.ordersystem.shared.exception.InvalidRequestException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;

/**
 * Valida DTOs de entrada con Jakarta Validation y convierte las violaciones en
 * {@link InvalidRequestException} (HTTP 400 con la lista de campos invalidos).
 */
@ApplicationScoped
public class RequestValidator {

    @Inject
    Validator validator;

    public <T> T validate(T request) {
        if (request == null) {
            throw new InvalidRequestException("Request body is required");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<String> details = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .toList();
            throw new InvalidRequestException("Request validation failed", details);
        }
        return request;
    }
}
