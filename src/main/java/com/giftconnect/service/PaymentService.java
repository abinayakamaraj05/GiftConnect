package com.giftconnect.service;

import com.giftconnect.dto.PaymentRequest;
import com.giftconnect.dto.PaymentResponse;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;
import com.giftconnect.entity.Payment;
import com.giftconnect.entity.PaymentMethod;
import com.giftconnect.entity.PaymentStatus;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.PaymentRepository;
import com.giftconnect.util.AccessControl;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ActivityLogService activityLogService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, OrderService orderService,
                          ActivityLogService activityLogService) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.activityLogService = activityLogService;
    }

    public PaymentResponse createPayment(PaymentRequest request, HttpServletRequest httpRequest) {
        Order order = orderService.findOrderOrThrow(request.getOrderId());
        // A payment may only be created for an order the caller owns (or by ADMIN) —
        // otherwise a customer could pay/tamper with someone else's order.
        AccessControl.requireOwnerOrAdmin(order.getUser().getUserId(), httpRequest);
        // Money may only be taken for a live order. Once it is CANCELLED or
        // DELIVERED, no new payment attempt is allowed.
        requirePayable(order);
        PaymentMethod method = parseMethod(request.getPaymentMethod());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());

        Payment saved = paymentRepository.save(payment);
        activityLogService.log(ActivityType.PAYMENT_CREATED, ActivityStatus.SUCCESS,
                httpRequest, "PAYMENT", saved.getPaymentId(),
                "Payment created for order " + order.getOrderId() + " via " + method);
        return PaymentResponse.fromEntity(saved);
    }

    public PaymentResponse getPaymentById(Long id, HttpServletRequest httpRequest) {
        Payment payment = findPaymentOrThrow(id);
        AccessControl.requireOwnerOrAdmin(payment.getOrder().getUser().getUserId(), httpRequest);
        return PaymentResponse.fromEntity(payment);
    }

    public List<PaymentResponse> getPaymentsByOrder(Long orderId, HttpServletRequest httpRequest) {
        Order order = orderService.findOrderOrThrow(orderId);
        AccessControl.requireOwnerOrAdmin(order.getUser().getUserId(), httpRequest);
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
    public PaymentResponse processPayment(Long id, boolean simulateFailure, HttpServletRequest httpRequest) {
        Payment payment = findPaymentOrThrow(id);
        // Only the order's owner or an ADMIN may drive a payment to its result.
        AccessControl.requireOwnerOrAdmin(payment.getOrder().getUser().getUserId(), httpRequest);

        // State check is separate from the permission check: a payment can only
        // be processed once, and only while it is still PENDING.
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new InvalidOperationException(
                    "Payment " + id + " has already been processed (status: " + payment.getPaymentStatus() + ")");
        }

        // The order may have moved to CANCELLED/DELIVERED since this PENDING
        // payment was created, so eligibility is re-checked here — before any
        // state is written. A rejected attempt leaves the payment PENDING and the
        // order untouched.
        requirePayable(payment.getOrder());

        if (simulateFailure) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            activityLogService.log(ActivityType.PAYMENT_FAILED, ActivityStatus.FAILURE,
                    httpRequest, "PAYMENT", payment.getPaymentId(),
                    "Payment failed (simulated) for order " + payment.getOrder().getOrderId());
            // Per spec: a failed payment leaves the order unpaid/pending — no order change here.
            return PaymentResponse.fromEntity(payment);
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        orderService.markOrderAsPaid(payment.getOrder());
        activityLogService.log(ActivityType.PAYMENT_PROCESSED, ActivityStatus.SUCCESS,
                httpRequest, "PAYMENT", payment.getPaymentId(),
                "Payment succeeded for order " + payment.getOrder().getOrderId());
        return PaymentResponse.fromEntity(payment);
    }

    private Payment findPaymentOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    /**
     * An order is payable only while it is still live. CANCELLED and DELIVERED
     * are terminal: no new payment may be created for them, and a payment that
     * was already PENDING must not be completed once the order reaches them.
     */
    private void requirePayable(Order order) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.DELIVERED) {
            throw new InvalidOperationException(
                    "Cannot pay for an order that is already " + status);
        }
    }

    private PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Invalid payment method: " + value);
        }
    }
}
