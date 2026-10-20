package com.giftconnect.dto;

import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only shape for an order in an API response. */
public class OrderResponse {

    private Long orderId;
    private Long userId;
    private String userEmail;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String paymentStatus;
    private String shippingAddress;
    private List<OrderItemResponse> items;

    // Signature feature — scheduled-delivery fields (all null on a non-scheduled order).
    private LocalDate requestedDeliveryDate;
    private String requestedTimeSlot;
    private String deliveryTimezone;
    private String recipientName;
    private String giftMessage;
    private LocalDateTime actualDeliveryDate;

    public static OrderResponse fromEntity(Order order) {
        OrderResponse response = new OrderResponse();
        response.orderId = order.getOrderId();
        response.userId = order.getUser().getUserId();
        response.userEmail = order.getUser().getEmail();
        response.orderDate = order.getOrderDate();
        response.totalAmount = order.getTotalAmount();
        response.status = order.getStatus();
        response.paymentStatus = order.getPaymentStatus();
        response.shippingAddress = order.getShippingAddress();
        response.items = order.getOrderItems().stream()
                .map(OrderItemResponse::fromEntity)
                .toList();
        response.requestedDeliveryDate = order.getRequestedDeliveryDate();
        response.requestedTimeSlot = order.getRequestedTimeSlot();
        response.deliveryTimezone = order.getDeliveryTimezone();
        response.recipientName = order.getRecipientName();
        response.giftMessage = order.getGiftMessage();
        response.actualDeliveryDate = order.getActualDeliveryDate();
        return response;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public LocalDate getRequestedDeliveryDate() {
        return requestedDeliveryDate;
    }

    public String getRequestedTimeSlot() {
        return requestedTimeSlot;
    }

    public String getDeliveryTimezone() {
        return deliveryTimezone;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getGiftMessage() {
        return giftMessage;
    }

    public LocalDateTime getActualDeliveryDate() {
        return actualDeliveryDate;
    }
}
