package com.taller.ordersystem.shared.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.Instant;
import java.util.List;

/** Construccion de respuestas de error homogeneas, compartida por los ExceptionMapper. */
final class ErrorResponses {

    private ErrorResponses() {
    }

    static Response build(Response.StatusType status, String code, String message, UriInfo uriInfo, List<String> details) {
        String path = uriInfo != null ? "/" + uriInfo.getPath().replaceFirst("^/", "") : null;
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.getStatusCode(), status.getReasonPhrase(), code, message, path, details);
        return Response.status(status.getStatusCode())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
