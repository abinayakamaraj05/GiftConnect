package com.giftconnect.dto;

import com.giftconnect.entity.Payment;
import com.giftconnect.entity.PaymentMethod;
import com.giftconnect.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String transactionRef;
    private LocalDateTime paymentDate;

    public static PaymentResponse fromEntity(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.paymentId = payment.getPaymentId();
        response.orderId = payment.getOrder().getOrderId();
        response.amount = payment.getAmount();
        response.paymentMethod = payment.getPaymentMethod();
        response.paymentStatus = payment.getPaymentStatus();
        response.transactionRef = payment.getTransactionRef();
        response.paymentDate = payment.getPaymentDate();
        return response;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }
}
