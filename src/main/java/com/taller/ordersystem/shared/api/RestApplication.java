package com.taller.ordersystem.shared.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Activa Jakarta REST bajo /api. Recursos y providers se descubren automaticamente. */
@ApplicationPath("/api")
public class RestApplication extends Application {
}
