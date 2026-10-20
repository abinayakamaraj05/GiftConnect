package com.giftconnect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * What the client sends to place a new order.
 *
 * There is deliberately NO userId field: the order is always placed FOR the
 * authenticated caller, and the owner is resolved server-side from the session
 * (AuthController -> AuthFilter) by OrderService. This makes it impossible for
 * a client to create an order on behalf of another user by changing a request
 * body field.
 *
 * Signature feature — the optional scheduling fields carry a requested delivery
 * date and preferred time slot. They are a REQUEST only; the server re-validates
 * the seven-calendar-day advance rule and the slot (see DeliveryPolicy) and
 * never trusts the front-end date picker.
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

    /**
     * Requested delivery date for a SCHEDULED gift. Optional; when present (or
     * when a slot is present) the order is treated as scheduled and both the date
     * and the slot are required and server-validated.
     */
    private LocalDate deliveryDate;

    /** Requested delivery time-slot code (e.g. "09:00-12:00"). See DeliverySlot. */
    private String deliverySlot;

    @Size(max = 100, message = "recipientName must be at most 100 characters")
    private String recipientName;

    @Size(max = 500, message = "giftMessage must be at most 500 characters")
    private String giftMessage;

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

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getGiftMessage() {
        return giftMessage;
    }

    public void setGiftMessage(String giftMessage) {
        this.giftMessage = giftMessage;
    }
}
