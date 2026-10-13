-- ============================================================
-- GiftConnect: Order idempotency (Phase 4I)
--
-- ADDITIVE ONLY — nothing here drops, renames, deletes, or backfills:
--   * one new table: order_idempotency
--   * one UNIQUE constraint on (user_id, idempotency_key)
--   * two foreign keys (users, orders), both ON DELETE CASCADE
--
-- Purpose
--   Records that a client's order-creation attempt (identified by the optional
--   "Idempotency-Key" request header) has already become exactly one order.
--   The UNIQUE constraint is the arbiter under concurrent retries: only one
--   row — and therefore only one order — can exist per (user, key).
--
-- Notes
--   * user_id is the AUTHENTICATED session user (never a client-supplied id):
--     the application always derives it from the server session.
--   * fingerprint is a SHA-256 hex digest of the canonical order input, used to
--     tell a retry of the same request from reuse of the key with new contents.
--   * The row is written in the SAME transaction as the order and its stock
--     changes, so a failed/rolled-back order leaves no orphaned key.
--
-- Verified against the live schema (read-only inspection):
--   users.user_id  BIGINT PRIMARY KEY
--   orders.order_id BIGINT PRIMARY KEY
--
-- ------------------------------------------------------------
-- Run this BEFORE starting an application build that uses the feature (optional
-- but recommended). Hibernate spring.jpa.hibernate.ddl-auto=update would also
-- create the table/constraint from the @Entity mapping, but this file documents
-- the exact names and actions explicitly.
--
-- ROLLBACK: DROP TABLE order_idempotency;  (drops only this new table —
--           no change to orders, order_items, products, or their data)
-- ============================================================

USE loved_ones_gifting;

CREATE TABLE IF NOT EXISTS order_idempotency (
    idempotency_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    fingerprint     CHAR(64)     NOT NULL,
    order_id        BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT uq_order_idempotency_user_key
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_idempotency_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- ---------- verification ----------
SHOW COLUMNS FROM order_idempotency;

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'order_idempotency';
