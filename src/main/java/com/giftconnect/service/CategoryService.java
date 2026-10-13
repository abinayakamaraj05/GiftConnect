package com.giftconnect.service;

import com.giftconnect.dto.CategoryRequest;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Category;
import com.giftconnect.exception.DuplicateResourceException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public CategoryService(CategoryRepository categoryRepository, ActivityLogService activityLogService) {
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
    }

    public Category addCategory(CategoryRequest request, HttpServletRequest httpRequest) {
        if (categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new DuplicateResourceException(
                    "Category already exists: " + request.getCategoryName());
        }
        Category category = new Category(request.getCategoryName(), request.getDescription());
        Category saved = categoryRepository.save(category);
        // Actor + IP are resolved from the session inside ActivityLogService —
        // never from the request body.
        activityLogService.log(ActivityType.CATEGORY_CREATED, ActivityStatus.SUCCESS,
                httpRequest, "CATEGORY", saved.getCategoryId(),
                "Category created: " + saved.getCategoryName());
        return saved;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    public Category updateCategory(Long id, CategoryRequest request, HttpServletRequest httpRequest) {
        Category category = getCategoryById(id);

        // Only enforce uniqueness if the name is actually changing
        boolean nameChanged = !category.getCategoryName().equalsIgnoreCase(request.getCategoryName());
        if (nameChanged && categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new DuplicateResourceException(
                    "Category already exists: " + request.getCategoryName());
        }

        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());
        Category saved = categoryRepository.save(category);
        activityLogService.log(ActivityType.CATEGORY_UPDATED, ActivityStatus.SUCCESS,
                httpRequest, "CATEGORY", saved.getCategoryId(),
                "Category updated: " + saved.getCategoryName());
        return saved;
    }

    public void deleteCategory(Long id, HttpServletRequest httpRequest) {
        Category category = getCategoryById(id);
        // If products still reference this category, the FK constraint in MySQL
        // will block this delete — GlobalExceptionHandler turns that into a 409.
        categoryRepository.delete(category);
        activityLogService.log(ActivityType.CATEGORY_DELETED, ActivityStatus.SUCCESS,
                httpRequest, "CATEGORY", id,
                "Category deleted: " + category.getCategoryName());
    }

    public List<Category> searchCategories(String keyword) {
        return categoryRepository.findByCategoryNameContainingIgnoreCase(keyword);
    }
}
