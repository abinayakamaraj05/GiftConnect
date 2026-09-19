package com.giftconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** What the client sends to create a payment for an order. */
public class PaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    /** Plain String (parsed to PaymentMethod in the service) — same reasoning as OrderStatusUpdateRequest. */
    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
