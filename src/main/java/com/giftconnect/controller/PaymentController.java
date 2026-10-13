package com.giftconnect.controller;

import com.giftconnect.dto.PaymentRequest;
import com.giftconnect.dto.PaymentResponse;
import com.giftconnect.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Protected by AuthFilter — every endpoint here requires an active login session.
 *
 * POST /api/payments                    -> create a (PENDING) payment for an order
 * GET  /api/payments/{id}                -> get one payment
 * GET  /api/payments/order/{orderId}     -> list payment attempts for an order
 * POST /api/payments/{id}/process        -> simulate the gateway response
 *
 * Every endpoint is scoped to the order's OWNER (or an ADMIN): the caller's id
 * is resolved from the session inside PaymentService, so a customer cannot
 * create, read, or process a payment against another customer's order. The
 * PENDING -> SUCCESS/FAILED state check also stays server-side.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request,
                                                         HttpServletRequest httpRequest) {
        PaymentResponse created = paymentService.createPayment(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentService.getPaymentById(id, httpRequest));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(@PathVariable Long orderId,
                                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId, httpRequest));
    }

    /**
     * simulateFailure defaults to false. Pass ?simulateFailure=true to
     * demonstrate the failure path in a viva without a real declined card.
     */
    @PostMapping("/{id}/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean simulateFailure,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentService.processPayment(id, simulateFailure, httpRequest));
    }
}
