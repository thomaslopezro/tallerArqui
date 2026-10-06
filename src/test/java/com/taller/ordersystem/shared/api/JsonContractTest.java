package com.taller.ordersystem.shared.api;

import com.taller.ordersystem.order.api.CreateOrderRequest;
import com.taller.ordersystem.order.domain.OrderStatus;
import com.taller.ordersystem.saga.OrderProcessStatus;
import com.taller.ordersystem.saga.OrderProcessView;
import com.taller.ordersystem.saga.SagaStep;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica con JSON-B (Yasson, el proveedor de WildFly) que los DTOs record se (de)serializan como espera la API. */
class JsonContractTest {

    private final Jsonb jsonb = JsonbBuilder.create();

    @Test
    void createOrderRequestIsReadFromJson() {
        CreateOrderRequest request = jsonb.fromJson(
                "{\"customerId\":\"c-1\",\"items\":[{\"productId\":5,\"quantity\":2}]}", CreateOrderRequest.class);

        assertEquals("c-1", request.customerId());
        assertEquals(5L, request.items().get(0).productId());
        assertEquals(2, request.items().get(0).quantity());
    }

    @Test
    void processViewIsWrittenAsJson() {
        OrderProcessView view = new OrderProcessView(1L, OrderProcessStatus.COMPLETED, SagaStep.DONE, null,
                OrderStatus.CONFIRMED, new BigDecimal("20.00"), null, null, null, Instant.now(), Instant.now());

        String json = jsonb.toJson(view);

        assertTrue(json.contains("\"processStatus\":\"COMPLETED\""), json);
        assertTrue(json.contains("\"orderStatus\":\"CONFIRMED\""), json);
        assertTrue(json.contains("\"orderTotal\":20.00"), json);
        assertTrue(json.contains("\"paymentStatus\":null"), json);
        assertTrue(json.contains("\"reservationStatus\":null"), json);
    }
}
