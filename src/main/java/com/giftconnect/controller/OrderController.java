package com.giftconnect.controller;

import com.giftconnect.dto.OrderRequest;
import com.giftconnect.dto.OrderResponse;
import com.giftconnect.dto.OrderStatusUpdateRequest;
import com.giftconnect.service.OrderService;
import com.giftconnect.util.AccessControl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Protected by AuthFilter (not in its public allowlist) — every endpoint
 * here requires an active login session, per the spec's authentication
 * safety rules.
 *
 * POST /api/orders                 -> place a new order FOR THE CALLER (owner from session)
 * GET  /api/orders                 -> list every order (ADMIN only)
 * GET  /api/orders/{id}            -> get one order (owner or ADMIN)
 * GET  /api/orders/user/{userId}   -> list one user's orders (that user or ADMIN)
 * PUT  /api/orders/{id}/status     -> change status (owner or ADMIN; forward-only transition)
 * PUT  /api/orders/{id}/cancel     -> cancel (owner or ADMIN; restocks items)
 *
 * The owner/role are always resolved server-side (AccessControl/OrderService),
 * so a customer cannot read or mutate another customer's orders by changing an
 * id in the path or body.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false)
                                                     String idempotencyKey,
                                                     HttpServletRequest httpRequest) {
        OrderResponse created = orderService.createOrder(request, idempotencyKey, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(HttpServletRequest httpRequest) {
        // Platform-wide order listing is an ADMIN view; sellers get their own
        // (seller-scoped) listing in Phase 3 once product ownership exists.
        AccessControl.requireAdmin(httpRequest);
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(orderService.getOrderById(id, httpRequest));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@PathVariable Long userId,
                                                               HttpServletRequest httpRequest) {
        // A customer may only list their own orders; ADMIN may list anyone's.
        AccessControl.requireOwnerOrAdmin(userId, httpRequest);
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody OrderStatusUpdateRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.getStatus(), httpRequest));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(orderService.cancelOrder(id, httpRequest));
    }
}
