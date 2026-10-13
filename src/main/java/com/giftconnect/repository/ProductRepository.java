package com.giftconnect.repository;

import com.giftconnect.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Phase 4G — loads one product row under a database write lock
     * (SELECT ... FOR UPDATE). Order creation and cancellation read/modify
     * stock with this lookup, so a concurrent checkout or restock of the same
     * product cannot read a stale quantity and overwrite the change. MUST be
     * called inside an active transaction; the lock is held until it commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

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

    /**
     * Phase 3 — every product owned by one seller. Powers the seller's own
     * inventory/management listing. Spring Data JPA reads "Seller_UserId" as:
     * follow the `seller` field on Product, then its `userId` field. A NULL
     * seller_id never matches, so legacy/platform products are excluded.
     */
    List<Product> findBySeller_UserId(Long sellerId);

    /**
     * Phase 3 — a seller's products filtered by status (e.g. ACTIVE only for a
     * public-facing catalog view). Optional convenience for the seller listing.
     */
    List<Product> findBySeller_UserIdAndStatus(Long sellerId, String status);
}
