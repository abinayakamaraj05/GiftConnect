package com.giftconnect.service;

import com.giftconnect.dto.ProductRequest;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Category;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.User;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.util.AccessControl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          ActivityLogService activityLogService, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
    }

    public ProductResponse addProduct(ProductRequest request, HttpServletRequest httpRequest) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        Product product = new Product();
        applyRequestToProduct(product, request, category);

        // Phase 3 — a SELLER always owns what they create; the owner id comes
        // ONLY from the authenticated session, never from the request body. An
        // ADMIN-created product is left unowned (seller_id NULL = platform-owned).
        if (AccessControl.isSeller(httpRequest)) {
            Long sellerId = AccessControl.requireActorId(httpRequest);
            User seller = userRepository.findById(sellerId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + sellerId));
            product.setSeller(seller);
        }

        Product saved = productRepository.save(product);
        activityLogService.log(ActivityType.PRODUCT_CREATED, ActivityStatus.SUCCESS,
                httpRequest, "PRODUCT", saved.getProductId(),
                "Product created: " + saved.getProductName());
        return ProductResponse.fromEntity(saved);
    }

    public List<ProductResponse> getAllProducts(HttpServletRequest httpRequest) {
        // Phase 3 — a SELLER sees their own inventory (all statuses, so they can
        // manage INACTIVE items too). ADMIN sees every product; everyone else
        // (anonymous / CUSTOMER) sees the Phase 2 public catalog: ACTIVE only.
        if (AccessControl.isSeller(httpRequest)) {
            Long sellerId = AccessControl.requireActorId(httpRequest);
            return toAllResponses(productRepository.findBySeller_UserId(sellerId));
        }
        return toVisibleResponses(productRepository.findAll(), httpRequest);
    }

    public ProductResponse getProductById(Long id, HttpServletRequest httpRequest) {
        return toVisibleResponse(findProductOrThrow(id), httpRequest);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request, HttpServletRequest httpRequest) {
        Product product = findProductOrThrow(id);

        // Phase 3 — ADMIN may edit anything; a SELLER only their own products.
        // A NULL owner (legacy/platform product) is editable by ADMIN only, so a
        // seller can never claim or modify an unowned row.
        AccessControl.requireOwnerOrAdmin(product.getSellerId(), httpRequest);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        applyRequestToProduct(product, request, category);
        Product saved = productRepository.save(product);
        activityLogService.log(ActivityType.PRODUCT_UPDATED, ActivityStatus.SUCCESS,
                httpRequest, "PRODUCT", saved.getProductId(),
                "Product updated: " + saved.getProductName());
        return ProductResponse.fromEntity(saved);
    }

    public void deleteProduct(Long id, HttpServletRequest httpRequest) {
        Product product = findProductOrThrow(id);

        // Phase 3 — same ownership rule as update: ADMIN any, SELLER own only,
        // NULL-owned products are ADMIN-only.
        AccessControl.requireOwnerOrAdmin(product.getSellerId(), httpRequest);

        productRepository.delete(product);
        activityLogService.log(ActivityType.PRODUCT_DELETED, ActivityStatus.SUCCESS,
                httpRequest, "PRODUCT", id,
                "Product deleted: " + product.getProductName());
    }

    public List<ProductResponse> searchProductsByName(String keyword, HttpServletRequest httpRequest) {
        return toVisibleResponses(productRepository.findByProductNameContainingIgnoreCase(keyword), httpRequest);
    }

    public List<ProductResponse> getProductsByCategory(Long categoryId, HttpServletRequest httpRequest) {
        // Fail fast with a clear 404 if the category itself doesn't exist,
        // rather than silently returning an empty list.
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }
        return toVisibleResponses(productRepository.findByCategory_CategoryId(categoryId), httpRequest);
    }

    /**
     * Used by GET /api/products/{id}/availability — a thin wrapper, but a
     * dedicated method keeps the "is this in stock" question explicit and
     * easy to extend later (e.g. reserved stock, low-stock threshold).
     */
    public ProductResponse checkAvailability(Long id, HttpServletRequest httpRequest) {
        return toVisibleResponse(findProductOrThrow(id), httpRequest);
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    /**
     * Phase 2 — anonymous catalog browsing. INACTIVE products are hidden from
     * everyone except ADMIN (who still sees the full catalogue in the management
     * table), so a public visitor can never discover retired stock.
     */
    private List<ProductResponse> toVisibleResponses(List<Product> products, HttpServletRequest httpRequest) {
        boolean includeInactive = AccessControl.isAdmin(httpRequest);
        return products.stream()
                .filter(product -> includeInactive || isActive(product))
                .map(ProductResponse::fromEntity)
                .toList();
    }

    /**
     * Maps every product regardless of status — used for a SELLER's own
     * inventory, where INACTIVE rows must stay visible for management.
     */
    private List<ProductResponse> toAllResponses(List<Product> products) {
        return products.stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    /**
     * Same rule for single-product reads, but Phase 3G also lets a SELLER read
     * their OWN product regardless of status. A non-admin asking for an INACTIVE
     * product they do not own gets a 404 (not 403) so its existence cannot be
     * probed.
     */
    private ProductResponse toVisibleResponse(Product product, HttpServletRequest httpRequest) {
        if (!isVisibleTo(product, httpRequest)) {
            throw new ResourceNotFoundException("Product not found with id: " + product.getProductId());
        }
        return ProductResponse.fromEntity(product);
    }

    /**
     * Phase 3G — single-product visibility. ACTIVE products stay public; INACTIVE
     * ones are visible only to an ADMIN or to the SELLER who owns them.
     *
     * A SELLER still cannot read another seller's INACTIVE product, nor an
     * unassigned (seller_id NULL) INACTIVE product; CUSTOMER and anonymous
     * callers always get 404 for INACTIVE. The caller is identified ONLY from the
     * server session (AccessControl/SessionActor), so this never trusts request
     * input and never leaks whether another seller's row exists.
     */
    private boolean isVisibleTo(Product product, HttpServletRequest httpRequest) {
        if (AccessControl.isAdmin(httpRequest)) {
            return true;
        }
        if (isActive(product)) {
            return true;
        }
        // INACTIVE: only the owning SELLER. isOwnerOrAdmin matches on session
        // actor id, so a NULL owner (unassigned) or a different seller -> false.
        return AccessControl.isSeller(httpRequest)
                && AccessControl.isOwnerOrAdmin(product.getSellerId(), httpRequest);
    }

    private boolean isActive(Product product) {
        return "ACTIVE".equalsIgnoreCase(product.getStatus());
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
