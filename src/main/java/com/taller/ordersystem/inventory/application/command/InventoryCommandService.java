package com.taller.ordersystem.inventory.application.command;

import com.taller.ordersystem.inventory.application.query.ReservationView;
import com.taller.ordersystem.inventory.domain.InventoryReservation;
import com.taller.ordersystem.inventory.domain.Product;
import com.taller.ordersystem.inventory.domain.ProductNotFoundException;
import com.taller.ordersystem.inventory.domain.ReservationItem;
import com.taller.ordersystem.inventory.persistence.InventoryReservationRepository;
import com.taller.ordersystem.inventory.persistence.ProductRepository;
import com.taller.ordersystem.shared.exception.InvalidStateException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Pasos de la SAGA que pertenecen al inventario: reservar (paso) y liberar (compensacion).
 *
 * <p>Ambos son {@code REQUIRES_NEW}: cada uno es una transaccion local, atomica e independiente
 * de los demas pasos de la SAGA. Si la reserva de cualquier linea falla, el rollback deshace
 * los descuentos de TODAS las lineas del pedido.</p>
 */
@ApplicationScoped
public class InventoryCommandService {

    private static final Logger LOG = Logger.getLogger(InventoryCommandService.class.getName());

    @Inject
    ProductRepository products;

    @Inject
    InventoryReservationRepository reservations;

    /**
     * Reserva el stock del pedido. Idempotente: si el pedido ya tiene reserva, la devuelve sin
     * volver a descontar stock.
     *
     * @throws com.taller.ordersystem.inventory.domain.InsufficientStockException si falta stock (rollback)
     * @throws ProductNotFoundException si algun producto ya no existe (rollback)
     */
    @Transactional(TxType.REQUIRES_NEW)
    public ReservationView reserve(Long orderId, List<ReservationLine> lines) {
        Optional<InventoryReservation> existing = reservations.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (existing.get().isReleased()) {
                throw new InvalidStateException("Reservation for order " + orderId + " was already released");
            }
            LOG.info(() -> "Inventory already reserved for order " + orderId + " - skipping (idempotent)");
            return ReservationView.from(existing.get());
        }

        InventoryReservation reservation = new InventoryReservation(orderId, Instant.now());
        // Orden determinista por productId: todas las transacciones bloquean filas en el mismo orden -> sin deadlocks.
        for (Map.Entry<Long, Integer> line : groupByProduct(lines).entrySet()) {
            Product product = products.findByIdForUpdate(line.getKey())
                    .orElseThrow(() -> new ProductNotFoundException(line.getKey()));
            product.reserve(line.getValue());
            reservation.addItem(product.getId(), line.getValue());
        }
        reservations.save(reservation);
        LOG.info(() -> "Inventory reserved for order " + orderId);
        return ReservationView.from(reservation);
    }

    /**
     * Compensacion: devuelve el stock reservado. Idempotente: si no hay reserva o ya fue liberada,
     * no hace nada (nunca devuelve stock dos veces).
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void release(Long orderId) {
        Optional<InventoryReservation> found = reservations.findByOrderIdForUpdate(orderId);
        if (found.isEmpty() || found.get().isReleased()) {
            LOG.info(() -> "Nothing to release for order " + orderId + " (idempotent)");
            return;
        }
        InventoryReservation reservation = found.get();
        reservation.getItems().stream()
                .sorted(Comparator.comparing(ReservationItem::getProductId))
                .forEach(item -> products.findByIdForUpdate(item.getProductId())
                        .orElseThrow(() -> new ProductNotFoundException(item.getProductId()))
                        .release(item.getQuantity()));
        reservation.release(Instant.now());
        LOG.info(() -> "Inventory released for order " + orderId);
    }

    private static Map<Long, Integer> groupByProduct(List<ReservationLine> lines) {
        Map<Long, Integer> grouped = new TreeMap<>();
        for (ReservationLine line : lines) {
            grouped.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return grouped;
    }
}
