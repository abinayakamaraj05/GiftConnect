-- ============================================================
-- GiftConnect: Order & Payment Management module
-- Run this against the EXISTING loved_ones_gifting database,
-- AFTER users, categories, and products already exist.
-- Does not touch or delete any existing table or data.
-- ============================================================

USE loved_ones_gifting;

-- ---------- orders ----------
-- NOTE: assumes the existing `users` table's primary key column is
-- named `user_id` (matching User.java in this codebase). If your real
-- users table uses a different column name, change user_id below to match.
CREATE TABLE IF NOT EXISTS orders (
    order_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    order_date       DATETIME NOT NULL,
    total_amount     DECIMAL(10,2) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    shipping_address VARCHAR(500),
    CONSTRAINT fk_order_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- ---------- order_items ----------
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT NOT NULL,
    product_id    BIGINT NOT NULL,
    quantity      INT NOT NULL,
    price         DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_orderitem_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_orderitem_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- ---------- payments ----------
CREATE TABLE IF NOT EXISTS payments (
    payment_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(100) NOT NULL,
    payment_date    DATETIME NOT NULL,
    CONSTRAINT uq_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);
