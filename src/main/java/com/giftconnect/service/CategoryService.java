package com.giftconnect.service;

import com.giftconnect.dto.CategoryRequest;
import com.giftconnect.entity.Category;
import com.giftconnect.exception.DuplicateResourceException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Autowired
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category addCategory(CategoryRequest request) {
        if (categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new DuplicateResourceException(
                    "Category already exists: " + request.getCategoryName());
        }
        Category category = new Category(request.getCategoryName(), request.getDescription());
        return categoryRepository.save(category);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    public Category updateCategory(Long id, CategoryRequest request) {
        Category category = getCategoryById(id);

        // Only enforce uniqueness if the name is actually changing
        boolean nameChanged = !category.getCategoryName().equalsIgnoreCase(request.getCategoryName());
        if (nameChanged && categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new DuplicateResourceException(
                    "Category already exists: " + request.getCategoryName());
        }

        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        // If products still reference this category, the FK constraint in MySQL
        // will block this delete — GlobalExceptionHandler turns that into a 409.
        categoryRepository.delete(category);
    }

    public List<Category> searchCategories(String keyword) {
        return categoryRepository.findByCategoryNameContainingIgnoreCase(keyword);
    }
}
