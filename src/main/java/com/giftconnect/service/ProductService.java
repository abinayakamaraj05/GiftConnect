package com.giftconnect.service;

import com.giftconnect.dto.ProductRequest;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.entity.Category;
import com.giftconnect.entity.Product;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse addProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        Product product = new Product();
        applyRequestToProduct(product, request, category);

        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    public ProductResponse getProductById(Long id) {
        return ProductResponse.fromEntity(findProductOrThrow(id));
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        applyRequestToProduct(product, request, category);
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    public List<ProductResponse> searchProductsByName(String keyword) {
        return productRepository.findByProductNameContainingIgnoreCase(keyword).stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        // Fail fast with a clear 404 if the category itself doesn't exist,
        // rather than silently returning an empty list.
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        return productRepository.findByCategory_CategoryId(categoryId).stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    /**
     * Used by GET /api/products/{id}/availability — a thin wrapper, but a
     * dedicated method keeps the "is this in stock" question explicit and
     * easy to extend later (e.g. reserved stock, low-stock threshold).
     */
    public ProductResponse checkAvailability(Long id) {
        return ProductResponse.fromEntity(findProductOrThrow(id));
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private void applyRequestToProduct(Product product, ProductRequest request, Category category) {
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(category);
        product.setImageUrl(request.getImageUrl());
        product.setStatus(
                request.getStatus() == null || request.getStatus().isBlank()
                        ? "ACTIVE"
                        : request.getStatus()
        );
    }
}
