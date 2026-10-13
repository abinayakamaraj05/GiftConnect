package com.giftconnect.entity;

/**
 * Classification of every auditable action on the platform. Used as a column
 * value on ActivityLog rows — an enum, never arbitrary free-form strings,
 * so admin filtering stays reliable.
 *
 * Naming: <AREA>_<ACTION>. Security/auth events are AUTH_*; domain events are
 * prefixed by their module (PRODUCT_, CATEGORY_, CART_, ORDER_, PAYMENT_).
 */
public enum ActivityType {
    // ----- Authentication / account security -----
    AUTH_LOGIN,
    AUTH_LOGIN_FAILED,
    AUTH_LOGOUT,
    AUTH_REGISTER,
    AUTH_OTP_SENT,
    AUTH_OTP_VERIFIED,
    AUTH_OTP_FAILED,
    AUTH_PASSWORD_CHANGED,

    // ----- Catalog -----
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    PRODUCT_VIEWED,

    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_DELETED,

    // ----- Cart (client-driven, logged at checkout/audit points) -----
    CART_ITEM_ADDED,
    CART_ITEM_REMOVED,
    CART_UPDATED,

    // ----- Orders -----
    ORDER_CREATED,
    ORDER_UPDATED,
    ORDER_CANCELLED,
    ORDER_STATUS_CHANGED,

    // ----- Payments -----
    PAYMENT_CREATED,
    PAYMENT_PROCESSED,
    PAYMENT_FAILED,

    // ----- Profile -----
    PROFILE_UPDATED,

    // ----- Admin actions -----
    ADMIN_USER_VIEWED,
    ADMIN_USER_UPDATED,
    ADMIN_USER_STATUS_CHANGED,
    ADMIN_USER_ROLE_CHANGED,
    ADMIN_REPORT_VIEWED
}