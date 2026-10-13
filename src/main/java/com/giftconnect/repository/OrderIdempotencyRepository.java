package com.giftconnect.repository;

import com.giftconnect.entity.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    /**
     * Phase 4I — looks up the record for one client key belonging to one user.
     * The lookup is always scoped by the authenticated user id, never by a
     * client-supplied owner. Backed by the named unique constraint
     * uq_order_idempotency_user_key.
     */
    Optional<OrderIdempotency> findByUser_UserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
