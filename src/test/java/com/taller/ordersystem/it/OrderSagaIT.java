package com.taller.ordersystem.it;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integracion end-to-end contra la aplicacion desplegada en WildFly + PostgreSQL.
 * Requieren la aplicacion levantada (docker compose up). Ejecutar con:
 * <pre>
 *   mvn verify -Pit                                   (Java local, app en localhost:8080)
 *   docker compose --profile test run --rm integration-tests   (sin Java local)
 * </pre>
 * Cada prueba crea sus propios productos, por lo que son independientes de los datos existentes.
 */
class OrderSagaIT {

    private static final String BASE_URL = System.getProperty("app.base-url", "http://localhost:8080");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void waitForApplication() throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(3).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                if (send("GET", "/api/health", null).statusCode() == 200) {
                    return;
                }
            } catch (RuntimeException notReadyYet) {
                // la app todavia esta arrancando
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Application not available at " + BASE_URL);
    }

    // ---------------------------------------------------------------- CASO A
    @Test
    void caseA_happyPath_confirmsOrderAndKeepsStockReserved() {
        long productId = createProduct("10.00", 10);
        long orderId = createOrder(productId, 2);

        // El precio actual cambia DESPUES de crear el pedido: el cobro usa el unitPrice historico
        assertEquals(200, send("PUT", "/api/products/" + productId + "/price", "{\"price\":99.99}").statusCode());

        HttpResponse<String> response = send("POST", "/api/orders/" + orderId + "/process", null);
        assertEquals(200, response.statusCode(), response.body());
        JsonObject process = json(response);
        assertEquals("COMPLETED", process.getString("processStatus"));
        assertEquals("CONFIRMED", process.getString("orderStatus"));
        assertEquals("RESERVED", process.getString("reservationStatus"));
        assertEquals("APPROVED", process.getString("paymentStatus"));
        assertEquals(0, new BigDecimal("20.00").compareTo(process.getJsonNumber("paymentAmount").bigDecimalValue()));
        assertEquals(8, stockOf(productId));
    }

    // ---------------------------------------------------------------- CASO B
    @Test
    void caseB_rejectedPayment_compensatesReleasingStockAndCancellingOrder() {
        long productId = createProduct("10.00", 10);
        long orderId = createOrder(productId, 2);

        JsonObject process = json(send("POST", "/api/orders/" + orderId + "/process?payment=REJECT", null));

        assertEquals("COMPENSATED", process.getString("processStatus"));
        assertEquals("CANCELLED", process.getString("orderStatus"));
        assertEquals("RELEASED", process.getString("reservationStatus"));
        assertEquals("REJECTED", process.getString("paymentStatus"));
        assertEquals(10, stockOf(productId));
    }

    // ---------------------------------------------------------------- CASO C
    @Test
    void caseC_insufficientStock_cancelsOrderWithoutPayment() {
        long productId = createProduct("10.00", 1);
        long orderId = createOrder(productId, 2);

        JsonObject process = json(send("POST", "/api/orders/" + orderId + "/process", null));

        assertEquals("COMPENSATED", process.getString("processStatus"));
        assertEquals("CANCELLED", process.getString("orderStatus"));
        assertTrue(isAbsentOrNull(process, "paymentStatus"), "no payment must be attempted");
        assertTrue(isAbsentOrNull(process, "reservationStatus"), "the failed reservation was rolled back");
        assertTrue(process.getString("lastError").contains("Insufficient stock"));
        assertEquals(1, stockOf(productId));
    }

    // ---------------------------------------------------------------- CASO D
    @Test
    void caseD_processingTwice_hasNoAdditionalEffects() {
        long productId = createProduct("10.00", 10);
        long orderId = createOrder(productId, 2);

        assertEquals(200, send("POST", "/api/orders/" + orderId + "/process", null).statusCode());
        HttpResponse<String> second = send("POST", "/api/orders/" + orderId + "/process", null);

        assertEquals(409, second.statusCode(), second.body());
        assertEquals("ORDER_ALREADY_PROCESSED", json(second).getString("code"));
        assertEquals(8, stockOf(productId), "stock must not be reserved twice");
        JsonObject process = json(send("GET", "/api/orders/" + orderId + "/process", null));
        assertEquals("COMPLETED", process.getString("processStatus"));
        assertEquals("APPROVED", process.getString("paymentStatus"));
    }

    @Test
    void concurrentProcessingOfTheSameOrderRunsTheSagaOnce() {
        long productId = createProduct("10.00", 10);
        long orderId = createOrder(productId, 2);

        List<Integer> statuses = runConcurrently(
                "/api/orders/" + orderId + "/process", "/api/orders/" + orderId + "/process");

        assertTrue(statuses.contains(200) && statuses.contains(409), "statuses: " + statuses);
        assertEquals(8, stockOf(productId));
    }

    // ---------------------------------------------------------------- Concurrencia de inventario
    @Test
    void concurrentOrdersForTheLastUnitNeverOversell() {
        long productId = createProduct("10.00", 1);
        long orderA = createOrder(productId, 1);
        long orderB = createOrder(productId, 1);

        runConcurrently("/api/orders/" + orderA + "/process", "/api/orders/" + orderB + "/process");

        List<String> results = List.of(
                json(send("GET", "/api/orders/" + orderA + "/process", null)).getString("processStatus"),
                json(send("GET", "/api/orders/" + orderB + "/process", null)).getString("processStatus"));
        assertTrue(results.contains("COMPLETED") && results.contains("COMPENSATED"), "results: " + results);
        assertEquals(0, stockOf(productId));
    }

    @Test
    void multiProductReservationRollsBackEveryLineWhenStockIsInsufficient() {
        long first = createProduct("10.00", 5);
        long second = createProduct("10.00", 0);
        String body = "{\"customerId\":\"rollback-test\",\"items\":[{\"productId\":" + first
                + ",\"quantity\":2},{\"productId\":" + second + ",\"quantity\":1}]}";
        HttpResponse<String> created = send("POST", "/api/orders", body);
        assertEquals(201, created.statusCode(), created.body());
        long orderId = json(created).getJsonNumber("id").longValue();
        HttpResponse<String> response = send("POST", "/api/orders/" + orderId + "/process", null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("COMPENSATED", json(response).getString("processStatus"));
        assertTrue(json(response).isNull("paymentStatus"));
        assertEquals(5, stockOf(first), "JTA must roll back the first line as well");
        assertEquals(0, stockOf(second));
    }

    @Test
    void concurrentPriceUpdatesDoNotRestoreReservedStock() {
        long productId = createProduct("10.00", 20);
        var requests = new java.util.ArrayList<CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 20; i++) {
            long orderId = createOrder(productId, 1);
            requests.add(CompletableFuture.supplyAsync(() ->
                    send("POST", "/api/orders/" + orderId + "/process", null)));
            requests.add(CompletableFuture.supplyAsync(() ->
                    send("PUT", "/api/products/" + productId + "/price", "{\"price\":12.00}")));
        }
        for (var request : requests) {
            HttpResponse<String> response = request.join();
            assertEquals(200, response.statusCode(), response.body());
        }
        assertEquals(0, stockOf(productId), "price changes must never overwrite a stock decrement");
    }

    // ---------------------------------------------------------------- API de productos y validaciones
    @Test
    void productCrudAndValidation() {
        long productId = createProduct("15.00", 5);

        assertEquals(200, send("GET", "/api/products/" + productId, null).statusCode());
        assertTrue(send("GET", "/api/products", null).body().contains("\"id\":" + productId));
        assertEquals(7, json(send("PUT", "/api/products/" + productId + "/stock", "{\"availableStock\":7}"))
                .getInt("availableStock"));
        JsonObject inventory = json(send("GET", "/api/products/" + productId + "/inventory", null));
        assertEquals(7, inventory.getInt("availableStock"));
        assertEquals(204, send("DELETE", "/api/products/" + productId, null).statusCode());
        assertEquals(404, send("GET", "/api/products/" + productId, null).statusCode());

        HttpResponse<String> invalid = send("POST", "/api/products", "{\"name\":\"\",\"price\":0,\"availableStock\":-1}");
        assertEquals(400, invalid.statusCode());
        assertEquals(3, json(invalid).getJsonArray("details").size());
    }

    @Test
    void orderValidation() {
        assertEquals(400, send("POST", "/api/orders", "{\"customerId\":\"c\",\"items\":[]}").statusCode());
        assertEquals(400, send("POST", "/api/orders", "{\"customerId\":\"\",\"items\":[{\"productId\":1,\"quantity\":0}]}").statusCode());
        assertEquals(404, send("POST", "/api/orders", "{\"customerId\":\"c\",\"items\":[{\"productId\":999999999,\"quantity\":1}]}").statusCode());
        assertEquals(404, send("GET", "/api/orders/999999999", null).statusCode());
        assertEquals(404, send("POST", "/api/orders/999999999/process", null).statusCode());
    }

    // ---------------------------------------------------------------- helpers

    private static long createProduct(String price, int stock) {
        String body = "{\"name\":\"IT product " + UUID.randomUUID() + "\",\"price\":" + price + ",\"availableStock\":" + stock + "}";
        HttpResponse<String> response = send("POST", "/api/products", body);
        assertEquals(201, response.statusCode(), response.body());
        return json(response).getJsonNumber("id").longValue();
    }

    private static long createOrder(long productId, int quantity) {
        String body = "{\"customerId\":\"it-customer\",\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + "}]}";
        HttpResponse<String> response = send("POST", "/api/orders", body);
        assertEquals(201, response.statusCode(), response.body());
        JsonObject order = json(response);
        assertEquals("PENDING", order.getString("status"));
        return order.getJsonNumber("id").longValue();
    }

    private static boolean isAbsentOrNull(JsonObject object, String key) {
        return !object.containsKey(key) || object.isNull(key);
    }

    private static int stockOf(long productId) {
        return json(send("GET", "/api/products/" + productId, null)).getInt("availableStock");
    }

    private static List<Integer> runConcurrently(String pathA, String pathB) {
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> send("POST", pathA, null).statusCode());
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> send("POST", pathB, null).statusCode());
        return List.of(a.join(), b.join());
    }

    private static JsonObject json(HttpResponse<String> response) {
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            JsonStructure structure = reader.read();
            if (structure.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new AssertionError("Expected JSON object: " + response.body());
            }
            return structure.asJsonObject();
        }
    }

    private static HttpResponse<String> send(String method, String path, String jsonBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        if (jsonBody != null) {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("HTTP call failed: " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
