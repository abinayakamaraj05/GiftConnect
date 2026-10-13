package com.giftconnect.controller;

import com.giftconnect.dto.ProductRequest;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.service.ProductService;
import com.giftconnect.util.AccessControl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Browser reads are public (Phase 2 — anonymous catalog browsing); writes stay
 * protected by AuthFilter and ADMIN-only here.
 *
 * POST   /api/products                       -> create  (SELLER or ADMIN; owner set from session)
 * GET    /api/products                       -> list   (public; ACTIVE for anon/CUSTOMER, own for SELLER, all for ADMIN)
 * GET    /api/products/{id}                  -> get one (public; 404 if INACTIVE unless ADMIN)
 * PUT    /api/products/{id}                  -> update  (ADMIN any; SELLER own only)
 * DELETE /api/products/{id}                  -> delete  (ADMIN any; SELLER own only)
 * GET    /api/products/category/{categoryId} -> filter by category (public)
 * GET    /api/products/search?name=...       -> search by partial name (public)
 * GET    /api/products/{id}/availability      -> stock/availability check (public)
 *
 * AuthFilter makes exactly these GET paths public; POST/PUT/DELETE on the same
 * paths still require a session.
 *
 * Phase 3 — seller ownership: the owner is NEVER read from the request; it is
 * taken from the authenticated session when a SELLER creates a product, and
 * update/delete re-check that the caller owns the row (AccessControl). A NULL
 * seller_id is a legacy/platform product that only an ADMIN may manage, so a
 * seller can neither claim nor modify unowned rows. The service layer also
 * hides INACTIVE products from anonymous/CUSTOMER callers.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request,
                                                      HttpServletRequest httpRequest) {
        // Phase 3 — a SELLER may create products (and is auto-assigned as owner
        // by the service); an ADMIN may create unowned/platform products.
        AccessControl.requireSellerOrAdmin(httpRequest);
        ProductResponse created = productService.addProduct(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(productService.getAllProducts(httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(productService.getProductById(id, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
                                                           @Valid @RequestBody ProductRequest request,
                                                           HttpServletRequest httpRequest) {
        // CUSTOMER/anonymous are rejected here (403); the service then enforces
        // per-product ownership so a SELLER can only update their own products.
        AccessControl.requireSellerOrAdmin(httpRequest);
        return ResponseEntity.ok(productService.updateProduct(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, HttpServletRequest httpRequest) {
        AccessControl.requireSellerOrAdmin(httpRequest);
        productService.deleteProduct(id, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId,
                                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, httpRequest));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String name,
                                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(productService.searchProductsByName(name, httpRequest));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<ProductResponse> checkAvailability(@PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(productService.checkAvailability(id, httpRequest));
    }
}
