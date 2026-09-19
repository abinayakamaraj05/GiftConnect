package com.giftconnect.controller;

import com.giftconnect.dto.PaymentRequest;
import com.giftconnect.dto.PaymentResponse;
import com.giftconnect.service.PaymentService;
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
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse created = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId));
    }

    /**
     * simulateFailure defaults to false. Pass ?simulateFailure=true to
     * demonstrate the failure path in a viva without a real declined card.
     */
    @PostMapping("/{id}/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {
        return ResponseEntity.ok(paymentService.processPayment(id, simulateFailure));
    }
}
