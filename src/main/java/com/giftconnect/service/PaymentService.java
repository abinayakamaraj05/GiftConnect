package com.giftconnect.service;

import com.giftconnect.dto.PaymentRequest;
import com.giftconnect.dto.PaymentResponse;
import com.giftconnect.entity.Order;
import com.giftconnect.entity.Payment;
import com.giftconnect.entity.PaymentMethod;
import com.giftconnect.entity.PaymentStatus;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Simulates a payment gateway for this MVP — there is no real UPI/card/
 * bank integration here. A payment is created in PENDING state, then
 * "processed" as a separate step to become SUCCESS or FAILED, matching
 * how a real gateway would call back asynchronously.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        Order order = orderService.findOrderOrThrow(request.getOrderId());
        PaymentMethod method = parseMethod(request.getPaymentMethod());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());

        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.fromEntity(saved);
    }

    public PaymentResponse getPaymentById(Long id) {
        return PaymentResponse.fromEntity(findPaymentOrThrow(id));
    }

    public List<PaymentResponse> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrder_OrderId(orderId).stream()
                .map(PaymentResponse::fromEntity)
                .toList();
    }

    /**
     * Simulates the gateway's response. There is no real bank/UPI call —
     * `simulateFailure` lets a demo deliberately show the failure path
     * without needing an actual declined card. Defaults to success.
     */
    @Transactional
    public PaymentResponse processPayment(Long id, boolean simulateFailure) {
        Payment payment = findPaymentOrThrow(id);

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new InvalidOperationException(
                    "Payment " + id + " has already been processed (status: " + payment.getPaymentStatus() + ")");
        }

        if (simulateFailure) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            // Per spec: a failed payment leaves the order unpaid/pending — no order change here.
            return PaymentResponse.fromEntity(payment);
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        orderService.markOrderAsPaid(payment.getOrder());
        return PaymentResponse.fromEntity(payment);
    }

    private Payment findPaymentOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Invalid payment method: " + value);
        }
    }
}
