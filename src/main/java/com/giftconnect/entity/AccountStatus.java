package com.giftconnect.entity;

/**
 * Whether an account may be used at all — deliberately separate from
 * {@code emailVerified}, which only says the address was proven.
 *
 *   ACTIVE   - normal account; login allowed (subject to email verification).
 *   INACTIVE - deactivated by an administrator. Login is refused, but the
 *              account and every related record (orders, payments, activity
 *              logs) stay exactly as they are. Nothing is ever deleted.
 *
 * Stored as a STRING enum (same approach as Role) so the database value stays
 * readable and reordering the constants can never silently change meaning.
 *
 * Phase 5 (ADMIN user management) is the only code allowed to change this.
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE
}
