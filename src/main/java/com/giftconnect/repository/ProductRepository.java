package com.giftconnect.repository;

import com.giftconnect.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Case-insensitive partial match on product name — powers /api/products/search?name=...
     */
    List<Product> findByProductNameContainingIgnoreCase(String keyword);

    /**
     * Filters products by their category's id — powers /api/products/category/{categoryId}.
     * Spring Data JPA reads "Category_CategoryId" as: follow the `category` field on
     * Product, then its `categoryId` field.
     */
    List<Product> findByCategory_CategoryId(Long categoryId);
}
