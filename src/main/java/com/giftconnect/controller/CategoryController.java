package com.giftconnect.controller;

import com.giftconnect.dto.CategoryRequest;
import com.giftconnect.entity.Category;
import com.giftconnect.service.CategoryService;
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
 * POST   /api/categories             -> create  (ADMIN only)
 * GET    /api/categories             -> list all (public)
 * GET    /api/categories/{id}        -> get one  (public)
 * PUT    /api/categories/{id}        -> update  (ADMIN only)
 * DELETE /api/categories/{id}        -> delete  (ADMIN only)
 * GET    /api/categories/search?name=... -> search by partial name (public)
 *
 * AuthFilter makes exactly these GET paths public; POST/PUT/DELETE on the same
 * paths still require a session.
 *
 * Authorization is enforced server-side via AccessControl, which resolves the
 * caller's role from the session AuthFilter populated at login — a client role
 * sent in a body/header/query is never trusted. Category mutations change the
 * platform-wide catalog, so they are ADMIN-only.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @Autowired
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<Category> addCategory(@Valid @RequestBody CategoryRequest request,
                                                HttpServletRequest httpRequest) {
        AccessControl.requireAdmin(httpRequest);
        Category created = categoryService.addCategory(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id,
                                                     @Valid @RequestBody CategoryRequest request,
                                                     HttpServletRequest httpRequest) {
        AccessControl.requireAdmin(httpRequest);
        return ResponseEntity.ok(categoryService.updateCategory(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, HttpServletRequest httpRequest) {
        AccessControl.requireAdmin(httpRequest);
        categoryService.deleteCategory(id, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Category>> searchCategories(@RequestParam String name) {
        return ResponseEntity.ok(categoryService.searchCategories(name));
    }
}
