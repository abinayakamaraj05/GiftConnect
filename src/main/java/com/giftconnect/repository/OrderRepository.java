package com.giftconnect.repository;

import com.giftconnect.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Powers GET /api/orders/user/{userId}. */
    List<Order> findByUser_UserId(Long userId);
}
