-- ============================================================
-- GiftConnect: Activity / Audit module (Phase 3)
--
-- Run this against the EXISTING loved_ones_gifting database.
--
-- SAFETY: this script is ADDITIVE ONLY.
--   * It never drops, truncates, renames, or deletes anything.
--   * It never modifies the existing users/categories/products/orders tables.
--   * It creates the activity_logs table if it is missing, and adds the
--     reporting indexes if they are missing.
--
-- The application also creates this table automatically on startup
-- (spring.jpa.hibernate.ddl-auto=update). Running this script first simply
-- makes the schema explicit for review/documentation, and adds the indexes
-- Hibernate does not create on its own.
-- ============================================================

USE loved_ones_gifting;

-- ---------- activity_logs ----------
-- Deliberate design choices:
--   * user_id is a PLAIN COLUMN (BIGINT), NOT a foreign key to users: an audit
--     row must survive the deletion of the account it refers to.
--   * email_snapshot / user_role are snapshots taken at event time.
--   * Nothing sensitive is ever written here: no passwords, password hashes,
--     OTP values, card numbers, CVV, tokens, or session ids.
CREATE TABLE IF NOT EXISTS activity_logs (
    log_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NULL,
    user_role      VARCHAR(20)  NULL,
    email_snapshot VARCHAR(100) NULL,
    activity_type  VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(40)  NULL,
    entity_id      BIGINT       NULL,
    description    VARCHAR(500) NULL,
    status         VARCHAR(10)  NOT NULL,
    ip_address     VARCHAR(45)  NULL,
    created_at     DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- reporting indexes ----------
-- The admin dashboard filters by type/role/status and always sorts by
-- created_at DESC, so these three cover the common queries.
-- MySQL has no "ADD INDEX IF NOT EXISTS"; each statement is safe to re-run
-- because a duplicate index name raises error 1061, which you can ignore.

ALTER TABLE activity_logs ADD INDEX idx_activity_created (created_at);
ALTER TABLE activity_logs ADD INDEX idx_activity_user (user_id);
ALTER TABLE activity_logs ADD INDEX idx_activity_type (activity_type);

-- ---------- verification ----------
DESCRIBE activity_logs;
SELECT COUNT(*) AS activity_rows FROM activity_logs;
