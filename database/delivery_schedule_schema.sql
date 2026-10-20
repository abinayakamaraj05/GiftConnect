-- ============================================================
-- GiftConnect: Scheduled Gift Delivery (signature feature)
-- Run this against the EXISTING loved_ones_gifting database.
-- Adds nullable columns to the existing `orders` table only, plus
-- one non-unique index on the new date column.
--
-- INTENDED USE
--   * If the application runs with spring.jpa.hibernate.ddl-auto=update (the
--     value in application.properties), Hibernate ALREADY adds these columns on
--     startup, so this script is NOT required.
--   * If the application runs with ddl-auto=validate or none (schema managed
--     outside the app, e.g. production), run this script once, before first
--     startup, to create the columns.
--   * Either way the script is IDEMPOTENT and safe to re-run: it adds a column
--     only when it is missing and creates the index only when it is missing.
--     (A plain "ADD COLUMN" would fail with error 1060 the second time, or as
--     soon as Hibernate has created the columns.)
--
-- NON-DESTRUCTIVE: every column is nullable and no existing column, row, or
-- constraint is dropped or altered. Existing orders simply have NULL scheduling
-- fields and behave exactly as before.
--
-- NOTE: MySQL 8 has no "ADD COLUMN IF NOT EXISTS" (that is a MariaDB
-- extension), so presence is checked against information_schema and the DDL is
-- executed conditionally.
--
-- ROLLBACK: ALTER TABLE orders
--             DROP COLUMN requested_delivery_date,
--             DROP COLUMN requested_time_slot,
--             DROP COLUMN recipient_name,
--             DROP COLUMN gift_message,
--             DROP COLUMN delivery_timezone,
--             DROP COLUMN actual_delivery_date;
--           DROP INDEX idx_orders_requested_delivery_date ON orders;
-- ============================================================

USE loved_ones_gifting;

-- ---------- columns ----------
-- Guard on one representative column: the six are added by a single atomic
-- ALTER, so they are either all present or all absent.
SET @columns_missing := (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'requested_delivery_date');

SET @ddl := IF(@columns_missing = 1,
    'ALTER TABLE orders
        ADD COLUMN requested_delivery_date DATE         NULL,
        ADD COLUMN requested_time_slot     VARCHAR(20)  NULL,
        ADD COLUMN recipient_name          VARCHAR(100) NULL,
        ADD COLUMN gift_message            VARCHAR(500) NULL,
        ADD COLUMN delivery_timezone       VARCHAR(40)  NULL,
        ADD COLUMN actual_delivery_date    DATETIME     NULL',
    'SELECT 1');

PREPARE add_delivery_columns FROM @ddl;
EXECUTE add_delivery_columns;
DEALLOCATE PREPARE add_delivery_columns;

-- ---------- index ----------
-- Supports admin "scheduled deliveries" views / date-range filtering.
SET @index_missing := (
    SELECT COUNT(*) = 0
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_requested_delivery_date');

SET @ddl := IF(@index_missing = 1,
    'CREATE INDEX idx_orders_requested_delivery_date ON orders (requested_delivery_date)',
    'SELECT 1');

PREPARE add_delivery_index FROM @ddl;
EXECUTE add_delivery_index;
DEALLOCATE PREPARE add_delivery_index;

-- ---------- verification ----------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'orders'
  AND COLUMN_NAME IN ('requested_delivery_date', 'requested_time_slot',
                      'recipient_name', 'gift_message',
                      'delivery_timezone', 'actual_delivery_date');
