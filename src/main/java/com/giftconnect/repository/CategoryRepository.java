package com.giftconnect.repository;

import com.giftconnect.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    /**
     * Powers the category search endpoint — matches partial, case-insensitive names.
     * e.g. searching "bir" matches "Birthday".
     */
    List<Category> findByCategoryNameContainingIgnoreCase(String keyword);
}
