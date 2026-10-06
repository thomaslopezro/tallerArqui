package com.taller.ordersystem.saga;

import com.taller.ordersystem.order.domain.OrderStatus;
import com.taller.ordersystem.shared.exception.InvalidStateException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.taller.ordersystem.order.persistence.OrderRepository;
import com.taller.ordersystem.order.domain.OrderNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cambios de estado del OrderProcess. Cada metodo es una transaccion REQUIRES_NEW propia:
 * el progreso de la SAGA queda registrado aunque un paso posterior falle y haga rollback.
 */
@ApplicationScoped
@Transactional(TxType.REQUIRES_NEW)
public class OrderProcessService {

    @Inject
    OrderProcessRepository processes;

    @Inject
    OrderRepository orders;

    /**
     * Registra el inicio del proceso o reanuda uno FAILED.
     *
     * <p>Garantiza que la SAGA de un pedido no se ejecute dos veces:</p>
     * <ul>
     *   <li>Si ya existe un proceso COMPLETED / COMPENSATED / RUNNING / COMPENSATING -> 409 sin efectos.</li>
     *   <li>Dos peticiones simultaneas: el bloqueo del pedido serializa el inicio incluso si aun no hay proceso.</li>
     * </ul>
     */
    public SagaMode begin(Long orderId, OrderStatus orderStatus) {
        // Una fila ausente no se puede bloquear: usamos el pedido, que ya existe.
        orderStatus = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)).getStatus();
        Optional<OrderProcess> existing = processes.findByOrderIdForUpdate(orderId);
        if (existing.isPresent()) {
            OrderProcess process = existing.get();
            if (process.getStatus() != OrderProcessStatus.FAILED) {
                throw new OrderAlreadyProcessedException(orderId, "process status is " + process.getStatus()
                        + ", order status is " + orderStatus);
            }
            return process.resume(Instant.now());
        }
        if (orderStatus != OrderStatus.PENDING) {
            throw new InvalidStateException("Order " + orderId + " must be PENDING to be processed (current: " + orderStatus + ")");
        }
        processes.saveAndFlush(new OrderProcess(orderId, Instant.now()));
        return SagaMode.FORWARD;
    }

    public void moveTo(Long orderId, SagaStep step) {
        update(orderId, p -> p.moveTo(step, Instant.now()));
    }

    public void startCompensation(Long orderId, String reason) {
        update(orderId, p -> p.startCompensation(reason, Instant.now()));
    }

    public void markCompleted(Long orderId) {
        update(orderId, p -> p.complete(Instant.now()));
    }

    public void markCompensated(Long orderId) {
        update(orderId, p -> p.markCompensated(Instant.now()));
    }

    public void markFailed(Long orderId, String error) {
        update(orderId, p -> p.fail(error, Instant.now()));
    }

    private void update(Long orderId, Consumer<OrderProcess> change) {
        OrderProcess process = processes.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new OrderProcessNotFoundException(orderId));
        change.accept(process);
    }
}
