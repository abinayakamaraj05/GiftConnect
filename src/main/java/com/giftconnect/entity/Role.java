package com.giftconnect.entity;

/**
 * The set of roles a User can have in the platform.
 *
 * CUSTOMER - browses/orders gifts (default for public registration).
 * SELLER   - manages their own products/inventory (Phase 2).
 * ADMIN    - manages the whole platform (users, orders, catalog).
 *
 * Public self-registration (AuthController -> UserService.registerUser)
 * always assigns CUSTOMER — SELLER and ADMIN accounts are never created
 * from user-supplied request data.
 */
public enum Role {
    CUSTOMER,
    SELLER,
    ADMIN
}
