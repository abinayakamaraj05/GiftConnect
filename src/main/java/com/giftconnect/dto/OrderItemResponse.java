package com.giftconnect.dto;

import com.giftconnect.entity.OrderItem;

import java.math.BigDecimal;

/** Read-only shape for one line of an order in an API response. */
public class OrderItemResponse {

    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;

    public static OrderItemResponse fromEntity(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.productId = item.getProduct().getProductId();
        response.productName = item.getProduct().getProductName();
        response.quantity = item.getQuantity();
        response.price = item.getPrice();
        response.subtotal = item.getSubtotal();
        return response;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
