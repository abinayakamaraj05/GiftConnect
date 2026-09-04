-- ============================================================
-- GiftConnect: Category & Product Management module
-- Run this against the EXISTING loved_ones_gifting database.
-- Does not touch the existing `users` table.
-- ============================================================

USE loved_ones_gifting;

-- ---------- categories ----------
CREATE TABLE IF NOT EXISTS categories (
    category_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    CONSTRAINT uq_category_name UNIQUE (category_name)
);

-- ---------- products ----------
CREATE TABLE IF NOT EXISTS products (
    product_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    description  VARCHAR(1000),
    price        DECIMAL(10,2) NOT NULL,
    stock        INT NOT NULL DEFAULT 0,
    category_id  BIGINT NOT NULL,
    image_url    VARCHAR(500),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- ============================================================
-- Sample data for testing (safe to skip if you'd rather add
-- your own via the API/Postman)
-- ============================================================

INSERT INTO categories (category_name, description) VALUES
    ('Birthday', 'Gifts to celebrate another trip around the sun'),
    ('Anniversary', 'Gifts for couples marking their special day'),
    ('Wedding', 'Gifts for the newly married'),
    ('Valentine''s Day', 'Romantic gifts for loved ones'),
    ('Flowers', 'Fresh flower bouquets and arrangements'),
    ('Chocolates', 'Chocolate boxes and gourmet sweets'),
    ('Personalized Gifts', 'Custom-engraved and made-to-order gifts');

INSERT INTO products (product_name, description, price, stock, category_id, image_url, status) VALUES
    ('Red Rose Bouquet', 'A dozen fresh red roses, hand-tied', 599.00, 25,
        (SELECT category_id FROM categories WHERE category_name = 'Flowers'),
        'https://example.com/images/red-roses.jpg', 'ACTIVE'),
    ('Belgian Chocolate Box', 'Assorted Belgian chocolates, 24 pieces', 899.00, 40,
        (SELECT category_id FROM categories WHERE category_name = 'Chocolates'),
        'https://example.com/images/belgian-chocolate.jpg', 'ACTIVE'),
    ('Engraved Photo Frame', 'Wooden photo frame with custom engraving', 449.00, 15,
        (SELECT category_id FROM categories WHERE category_name = 'Personalized Gifts'),
        'https://example.com/images/photo-frame.jpg', 'ACTIVE'),
    ('Birthday Balloon Bouquet', 'Set of 12 colorful birthday balloons', 299.00, 50,
        (SELECT category_id FROM categories WHERE category_name = 'Birthday'),
        'https://example.com/images/balloons.jpg', 'ACTIVE'),
    ('Anniversary Mug Set', 'Matching "Mr & Mrs" ceramic mug set', 549.00, 0,
        (SELECT category_id FROM categories WHERE category_name = 'Anniversary'),
        'https://example.com/images/mug-set.jpg', 'INACTIVE');
