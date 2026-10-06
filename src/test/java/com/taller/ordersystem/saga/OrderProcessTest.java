package com.taller.ordersystem.saga;

import com.taller.ordersystem.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderProcessTest {

    @Test
    void failedProcessResumesFromTheInterruptedPhase() {
        OrderProcess forward = new OrderProcess(1L, Instant.now());
        forward.moveTo(SagaStep.CONFIRM_ORDER, Instant.now());
        forward.fail("timeout", Instant.now());
        assertEquals(SagaMode.FORWARD, forward.resume(Instant.now()));
        assertEquals(OrderProcessStatus.RUNNING, forward.getStatus());

        OrderProcess compensating = new OrderProcess(2L, Instant.now());
        compensating.startCompensation("payment rejected", Instant.now());
        compensating.moveTo(SagaStep.RELEASE_INVENTORY, Instant.now());
        compensating.fail("timeout", Instant.now());
        assertEquals(SagaMode.COMPENSATION, compensating.resume(Instant.now()));
        assertEquals(OrderProcessStatus.COMPENSATING, compensating.getStatus());
    }

    @Test
    void failureBeforeFirstCompensationStillResumesCompensation() {
        OrderProcess process = new OrderProcess(3L, Instant.now());
        process.moveTo(SagaStep.RESERVE_INVENTORY, Instant.now());
        process.startCompensation("insufficient stock", Instant.now());
        // Simula un fallo al registrar el primer paso, despues del commit de startCompensation.
        process.fail("database unavailable", Instant.now());
        assertEquals(SagaMode.COMPENSATION, process.resume(Instant.now()));
        assertEquals(OrderProcessStatus.COMPENSATING, process.getStatus());
    }

    @Test
    void finishedProcessCannotBeResumed() {
        OrderProcess process = new OrderProcess(1L, Instant.now());
        process.complete(Instant.now());

        assertThrows(InvalidStateException.class, () -> process.resume(Instant.now()));
        assertEquals(SagaStep.DONE, process.getCurrentStep());
    }
}
