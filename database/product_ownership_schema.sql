-- ============================================================
-- GiftConnect: Seller ownership (Phase 3)
--
-- ADDITIVE ONLY — nothing here drops, renames, deletes, or backfills:
--   * the products table gains ONE nullable column: seller_id
--   * one index for owner-scoped listings
--   * one foreign key to users(user_id) with ON DELETE SET NULL / ON UPDATE CASCADE
--
-- seller_id is NULLABLE on purpose:
--   * NULL means a legacy/platform-owned product (created by an ADMIN, or
--     seeded before ownership existed). Only an ADMIN may manage such a row.
--   * existing products are intentionally NOT assigned to any seller.
--
-- The application (ProductService) always sets seller_id from the
-- authenticated session when a SELLER creates a product; a client can never
-- supply an owner id. Deactivating/deleting a seller account does NOT remove
-- their products: the FK sets seller_id back to NULL (platform-owned).
--
-- Verified against the live schema (read-only inspection):
--   products.product_id BIGINT PRIMARY KEY
--   products.category_id BIGINT  -> categories.category_id (existing FK)
--   users.user_id        BIGINT PRIMARY KEY
--
-- ------------------------------------------------------------
-- Run this BEFORE starting the application (optional but recommended).
-- Hibernate spring.jpa.hibernate.ddl-auto=update would also add the nullable
-- column, but this file documents the exact index + FK (and their delete/update
-- actions) explicitly, and avoids relying on Hibernate's generated names.
--
-- DO NOT make seller_id NOT NULL and DO NOT backfill it: on a non-empty table
-- MySQL would fill '' (an unreadable id), and guessing an owner would be wrong.
-- ============================================================

USE loved_ones_gifting;

ALTER TABLE products
    ADD COLUMN seller_id BIGINT NULL;

ALTER TABLE products
    ADD INDEX idx_products_seller (seller_id);

ALTER TABLE products
    ADD CONSTRAINT fk_product_seller
        FOREIGN KEY (seller_id) REFERENCES users(user_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;

-- ---------- verification ----------
SHOW COLUMNS FROM products LIKE 'seller_id';

SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'products'
  AND COLUMN_NAME = 'seller_id';

-- Every current product should still be platform-owned (seller_id IS NULL).
-- This is expected and requires no backfill.
SELECT COUNT(*) AS products_total,
       SUM(seller_id IS NULL) AS products_unowned
FROM products;
