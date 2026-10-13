package com.giftconnect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What the client sends to place a new order.
 *
 * There is deliberately NO userId field: the order is always placed FOR the
 * authenticated caller, and the owner is resolved server-side from the session
 * (AuthController -> AuthFilter) by OrderService. This makes it impossible for
 * a client to create an order on behalf of another user by changing a request
 * body field.
 */
public class OrderRequest {

    /**
     * Required and bounded to the Order.shipping_address column width (500), so
     * a blank or over-long address is rejected as a 400 validation error instead
     * of surfacing as a database integrity violation.
     */
    @NotBlank(message = "shippingAddress is required")
    @Size(max = 500, message = "shippingAddress must be at most 500 characters")
    private String shippingAddress;

    @NotEmpty(message = "An order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
}
