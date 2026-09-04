package com.restapi.controller;

import com.restapi.caching.ETagHelper;
import com.restapi.model.HalResource;
import com.restapi.model.HateoasHelper;
import com.restapi.model.Order;
import com.restapi.model.OrderStatus;
import com.restapi.pagination.Cursor;
import com.restapi.pagination.CursorPage;
import com.restapi.pagination.CursorPaginationHelper;
import com.restapi.problem.OrderNotFoundException;
import com.restapi.problem.PreconditionFailedException;
import com.restapi.problem.UnprocessableOrderException;
import com.restapi.sse.SseOrderEventStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-Maturity REST API Order Controller demonstrating:
 * <ul>
 *   <li><b>HATEOAS (Richardson Level 3):</b> Dynamic {@code _links} based on order state.</li>
 *   <li><b>HTTP Caching & ETags:</b> {@code If-None-Match} -> 304 Not Modified, {@code If-Match} -> 412.</li>
 *   <li><b>Cursor Pagination:</b> Opaque Base64 cursors avoiding SQL offset degradation.</li>
 *   <li><b>Server-Sent Events (SSE):</b> Live order lifecycle event streaming.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    public record CreateOrderRequest(@NotNull @Positive BigDecimal amount, String currency) {}
    public record UpdateOrderRequest(@NotNull @Positive BigDecimal amount) {}

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final SseOrderEventStream sseStream;

    public OrderController(SseOrderEventStream sseStream) {
        this.sseStream = sseStream;
        // Seed default orders
        orders.put("ord_1", new Order("ord_1", OrderStatus.PENDING, new BigDecimal("149.99"), "USD", 1, Instant.now().minusSeconds(3600), Instant.now()));
        orders.put("ord_2", new Order("ord_2", OrderStatus.PAID, new BigDecimal("299.50"), "USD", 1, Instant.now().minusSeconds(1800), Instant.now()));
    }

    @GetMapping
    public ResponseEntity<CursorPage<Order>> listOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Optional<Cursor> decodedCursor = Cursor.decode(cursor);
        List<Order> allOrders = new ArrayList<>(orders.values());
        CursorPage<Order> page = CursorPaginationHelper.paginate(allOrders, decodedCursor, status, limit);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id, HttpServletRequest request) {
        Order order = orders.get(id);
        if (order == null) {
            throw new OrderNotFoundException("Order with ID '" + id + "' does not exist");
        }

        String etag = order.eTag();

        // RFC 9110 Conditional GET: If-None-Match
        if (ETagHelper.isNotModified(request, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60, must-revalidate")
                    .build();
        }

        // Richardson Level 3 HATEOAS response
        HalResource<Order> hal = HateoasHelper.wrapWithHal("", order);
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60, must-revalidate")
                .body(hal);
    }

    @PostMapping
    public ResponseEntity<HalResource<Order>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        String id = "ord_" + UUID.randomUUID().toString().substring(0, 8);
        Order order = new Order(id, OrderStatus.PENDING, req.amount(), req.currency(), 1, Instant.now(), Instant.now());
        orders.put(id, order);

        sseStream.broadcastEvent("ORDER_CREATED", order);

        HalResource<Order> hal = HateoasHelper.wrapWithHal("", order);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + id))
                .eTag(order.eTag())
                .body(hal);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HalResource<Order>> updateOrder(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderRequest req,
            HttpServletRequest request
    ) {
        Order current = orders.get(id);
        if (current == null) {
            throw new OrderNotFoundException("Order with ID '" + id + "' does not exist");
        }

        // Optimistic Concurrency Control with If-Match
        if (!ETagHelper.isMatch(request, current.eTag())) {
            throw new PreconditionFailedException("If-Match ETag mismatch. Order was modified by another request.");
        }

        Order updated = current.withAmount(req.amount(), current.version() + 1);
        orders.put(id, updated);

        HalResource<Order> hal = HateoasHelper.wrapWithHal("", updated);
        return ResponseEntity.ok()
                .eTag(updated.eTag())
                .body(hal);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<HalResource<Order>> payOrder(@PathVariable String id) {
        Order current = orders.get(id);
        if (current == null) {
            throw new OrderNotFoundException("Order with ID '" + id + "' does not exist");
        }

        if (current.status() != OrderStatus.PENDING) {
            throw new UnprocessableOrderException("Cannot pay for order with status '" + current.status() + "'. Must be PENDING.");
        }

        Order paid = current.withStatusAndVersion(OrderStatus.PAID, current.version() + 1);
        orders.put(id, paid);

        sseStream.broadcastEvent("ORDER_PAID", paid);

        HalResource<Order> hal = HateoasHelper.wrapWithHal("", paid);
        return ResponseEntity.ok()
                .eTag(paid.eTag())
                .body(hal);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<HalResource<Order>> cancelOrder(@PathVariable String id) {
        Order current = orders.get(id);
        if (current == null) {
            throw new OrderNotFoundException("Order with ID '" + id + "' does not exist");
        }

        if (current.status() == OrderStatus.CANCELLED || current.status() == OrderStatus.COMPLETED) {
            throw new UnprocessableOrderException("Cannot cancel order with status '" + current.status() + "'.");
        }

        Order cancelled = current.withStatusAndVersion(OrderStatus.CANCELLED, current.version() + 1);
        orders.put(id, cancelled);

        sseStream.broadcastEvent("ORDER_CANCELLED", cancelled);

        HalResource<Order> hal = HateoasHelper.wrapWithHal("", cancelled);
        return ResponseEntity.ok()
                .eTag(cancelled.eTag())
                .body(hal);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return sseStream.createEmitter();
    }
}
