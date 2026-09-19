package com.giftconnect.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for PUT /api/orders/{id}/status.
 * status is a plain String here (not the OrderStatus enum) so an invalid
 * value like "SHIPED" (typo) fails with a clean, readable error message
 * from OrderService instead of a raw Jackson deserialization exception.
 */
public class OrderStatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
