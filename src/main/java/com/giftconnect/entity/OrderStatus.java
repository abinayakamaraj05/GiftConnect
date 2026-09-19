package com.giftconnect.entity;

/**
 * The lifecycle states of an Order. Kept to exactly the values the
 * spec asked for — no extra statuses.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
