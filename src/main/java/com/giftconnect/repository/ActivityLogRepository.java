package com.giftconnect.repository;

import com.giftconnect.entity.ActivityLog;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read side of the audit trail. Admin filtering + pagination — the log page
 * must never load thousands of rows at once, so every query is Pageable.
 */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Combined filter for GET /api/admin/activity. Every parameter is nullable —
     * a null is "no constraint" via the (1 = 1 OR ...) idiom, so the service can
     * pass whichever filters the admin chose and the database does the filtering
     * (no in-memory filtering of large sets).
     */
    @Query("""
            SELECT l FROM ActivityLog l
            WHERE (:userId IS NULL OR l.userId = :userId)
              AND (:role IS NULL OR l.userRole = :role)
              AND (:type IS NULL OR l.activityType = :type)
              AND (:entityType IS NULL OR l.entityType = :entityType)
              AND (:status IS NULL OR l.status = :status)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
            ORDER BY l.createdAt DESC
            """)
    Page<ActivityLog> findByFilters(@Param("userId") Long userId,
                                    @Param("role") Role role,
                                    @Param("type") ActivityType type,
                                    @Param("entityType") String entityType,
                                    @Param("status") ActivityStatus status,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    Pageable pageable);

    /** Recent security/auth events for the admin dashboard. */
    Page<ActivityLog> findByActivityTypeInOrderByCreatedAtDesc(List<ActivityType> types, Pageable pageable);
}