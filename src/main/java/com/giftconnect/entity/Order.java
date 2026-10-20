package com.giftconnect.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the "orders" table. One order belongs to one User and
 * contains one or more OrderItems.
 *
 * NOTE: this entity is never returned directly by a controller —
 * OrderResponse (a DTO) is used instead, so the bidirectional link to
 * OrderItem never has to be serialized by Jackson.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Simple PENDING / PAID / FAILED flag, kept as plain text (matching the
     * existing Product.status pattern) rather than a full enum, since only
     * these three values are needed for this MVP.
     */
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "PENDING";

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    /**
     * Signature feature — the date the customer REQUESTED delivery for. Null on
     * an ordinary (non-scheduled) order. Always validated server-side against the
     * seven-calendar-day advance rule (see DeliveryPolicy).
     */
    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    /**
     * The requested delivery time-slot code (e.g. "09:00-12:00"). Null unless the
     * order is scheduled. A PREFERRED window, stored exactly as the stable
     * DeliverySlot code so it never drifts with display text.
     */
    @Column(name = "requested_time_slot", length = 20)
    private String requestedTimeSlot;

    /** Recipient's name for a gift order (may differ from the ordering customer). */
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    /** Optional personal message to include with the gift. */
    @Column(name = "gift_message", length = 500)
    private String giftMessage;

    /** IANA zone the delivery date was chosen in (e.g. "Asia/Kolkata"). */
    @Column(name = "delivery_timezone", length = 40)
    private String deliveryTimezone;

    /**
     * When the order was ACTUALLY delivered. Set only when an admin/operator moves
     * the order to DELIVERED — never derived from the requested date, so a passed
     * scheduled time is never mistaken for a completed delivery.
     */
    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public Order() {
    }

    @PrePersist
    protected void onCreate() {
        this.orderDate = LocalDateTime.now();
    }

    // ----- Getters and setters -----

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public LocalDate getRequestedDeliveryDate() {
        return requestedDeliveryDate;
    }

    public void setRequestedDeliveryDate(LocalDate requestedDeliveryDate) {
        this.requestedDeliveryDate = requestedDeliveryDate;
    }

    public String getRequestedTimeSlot() {
        return requestedTimeSlot;
    }

    public void setRequestedTimeSlot(String requestedTimeSlot) {
        this.requestedTimeSlot = requestedTimeSlot;
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

    public String getDeliveryTimezone() {
        return deliveryTimezone;
    }

    public void setDeliveryTimezone(String deliveryTimezone) {
        this.deliveryTimezone = deliveryTimezone;
    }

    public LocalDateTime getActualDeliveryDate() {
        return actualDeliveryDate;
    }

    public void setActualDeliveryDate(LocalDateTime actualDeliveryDate) {
        this.actualDeliveryDate = actualDeliveryDate;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    /** Convenience method that keeps both sides of the relationship in sync. */
    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }
}
