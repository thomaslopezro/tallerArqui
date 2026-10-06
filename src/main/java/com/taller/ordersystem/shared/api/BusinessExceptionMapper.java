package com.taller.ordersystem.shared.api;

import com.taller.ordersystem.shared.exception.BusinessException;
import com.taller.ordersystem.shared.exception.InvalidRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/** Traduce errores de negocio a 400 / 404 / 409 con cuerpo JSON consistente. */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(BusinessException exception) {
        Response.Status status = switch (exception.getType()) {
            case INVALID_REQUEST -> Response.Status.BAD_REQUEST;
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case CONFLICT -> Response.Status.CONFLICT;
        };
        List<String> details = exception instanceof InvalidRequestException invalid ? invalid.getDetails() : List.of();
        return ErrorResponses.build(status, exception.getCode(), exception.getMessage(), uriInfo, details);
    }
}
