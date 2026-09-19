package com.giftconnect.dto;

import com.giftconnect.entity.Product;

import java.math.BigDecimal;

/**
 * What the API returns for a product. Flattens the nested Category
 * relationship into categoryId + categoryName so the frontend gets a
 * simple flat object instead of having to dig into product.category.categoryName,
 * and so we never accidentally serialize a lazy-loaded proxy.
 */
public class ProductResponse {

    private Long productId;
    private String productName;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;
    private String status;
    private boolean inStock;

    public static ProductResponse fromEntity(Product product) {
        ProductResponse response = new ProductResponse();
        response.productId = product.getProductId();
        response.productName = product.getProductName();
        response.description = product.getDescription();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.categoryId = product.getCategory().getCategoryId();
        response.categoryName = product.getCategory().getCategoryName();
        response.imageUrl = product.getImageUrl();
        response.status = product.getStatus();
        response.inStock = product.getStock() != null && product.getStock() > 0;
        return response;
    }

    // ----- Getters (no setters needed — this is a read-only response shape) -----

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public boolean isInStock() {
        return inStock;
    }
}
