package com.giftconnect.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What the client sends when creating or updating a category.
 * Kept separate from the Category entity so the API shape doesn't
 * depend on how we map the database table.
 */
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    private String categoryName;

    private String description;

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
