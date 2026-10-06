package com.taller.ordersystem.shared.api;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Red de seguridad para todo lo que no es un error de negocio:
 * <ul>
 *   <li>Errores propios de Jakarta REST (404 de ruta, 405, 415...): se respeta su codigo.</li>
 *   <li>JSON mal formado: 400.</li>
 *   <li>Cualquier otro fallo tecnico: 500, registrado en el log sin exponer detalles internos.</li>
 * </ul>
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class.getName());

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException web) {
            Response.StatusType status = web.getResponse().getStatusInfo();
            if (hasCause(exception, JsonbException.class)) {
                return ErrorResponses.build(Response.Status.BAD_REQUEST, "MALFORMED_JSON",
                        "Request body is not valid JSON for this endpoint", uriInfo, List.of());
            }
            return ErrorResponses.build(status, "HTTP_" + status.getStatusCode(), web.getMessage(), uriInfo, List.of());
        }
        if (exception instanceof ProcessingException || hasCause(exception, JsonbException.class)) {
            return ErrorResponses.build(Response.Status.BAD_REQUEST, "MALFORMED_JSON",
                    "Request body is not valid JSON for this endpoint", uriInfo, List.of());
        }
        LOG.log(Level.SEVERE, "Unexpected technical error", exception);
        return ErrorResponses.build(Response.Status.INTERNAL_SERVER_ERROR, "TECHNICAL_ERROR",
                "Unexpected technical error",
                uriInfo, List.of());
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
