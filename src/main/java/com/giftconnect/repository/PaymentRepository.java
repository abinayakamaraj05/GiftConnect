package com.giftconnect.repository;

import com.giftconnect.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Powers GET /api/payments/order/{orderId} — a list, since a failed
     *  attempt can be followed by a retry, leaving more than one payment
     *  record against the same order. */
    List<Payment> findByOrder_OrderId(Long orderId);
}
