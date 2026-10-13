-- ============================================================
-- GiftConnect: ADMIN user management (Phase 5)
--
-- ADDITIVE ONLY — nothing here drops, renames, or deletes anything:
--   * the users table gains ONE new column: account_status
--   * one index for the admin list/filter queries
--   * no other table is touched; orders, payments, and activity logs are
--     left completely alone
--
-- ------------------------------------------------------------
-- !! RUN THIS BEFORE STARTING THE APPLICATION (IMPORTANT) !!
-- ------------------------------------------------------------
-- The column is added WITH a default on purpose. Hibernate's
-- spring.jpa.hibernate.ddl-auto=update would add it as NOT NULL WITHOUT a
-- default, and on a table that already has rows MySQL would backfill '' —
-- a value that cannot be read back into the AccountStatus enum.
-- With DEFAULT 'ACTIVE' below every existing account simply becomes ACTIVE
-- (nobody is locked out) and the application starts cleanly.
-- ============================================================

USE loved_ones_gifting;

ALTER TABLE users
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users
    ADD INDEX idx_users_status (account_status);

-- ---------- verification ----------
SELECT account_status, COUNT(*) AS accounts
FROM users
GROUP BY account_status;

DESCRIBE users;

-- ============================================================
-- FALLBACK: only if the application was started FIRST and the column already
-- exists without a default (symptom: "Cannot convert '' to AccountStatus"):
--
--   UPDATE users SET account_status = 'ACTIVE'
--    WHERE account_status IS NULL OR account_status = '';
--   ALTER TABLE users MODIFY account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- ============================================================
