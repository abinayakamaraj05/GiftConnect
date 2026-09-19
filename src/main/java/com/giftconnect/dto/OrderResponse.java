package com.giftconnect.dto;

import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;

import java.math.BigDecimal;
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
}
