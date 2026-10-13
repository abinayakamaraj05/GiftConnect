package com.giftconnect.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Maps to the "products" table. Each product belongs to exactly one
 * Category (many-to-one) via the category_id foreign key.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @NotBlank(message = "Product name is required")
    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "description", length = 1000)
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Column(name = "stock", nullable = false)
    private Integer stock;

    /**
     * Many products belong to one category. FetchType.LAZY avoids loading the
     * whole Category row unless it's actually accessed — good default for
     * a @ManyToOne relationship.
     */
    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Simple text status ("ACTIVE" / "INACTIVE") rather than a Java enum —
     * keeps this MVP simple. Defaults to ACTIVE if not provided.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    /**
     * Phase 3 — the SELLER who owns this product. Deliberately NULLABLE:
     * a NULL seller_id means a legacy/platform-owned product (e.g. created by
     * an ADMIN or seeded before ownership existed), which only an ADMIN may
     * manage. A SELLER may only manage rows whose seller_id matches their own
     * session user id. The owner is always taken from the authenticated session
     * (never from client input) — see ProductService.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    public Product() {
    }

    // ----- Getters and setters -----

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    /**
     * The owner's user id, or null for a legacy/platform-owned product. Kept as
     * a convenience so authorization checks never have to dereference a lazy
     * proxy: AccessControl.requireOwnerOrAdmin(getSellerId(), request).
     */
    public Long getSellerId() {
        return seller == null ? null : seller.getUserId();
    }
}
