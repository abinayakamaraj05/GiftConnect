package com.giftconnect.service;

import com.giftconnect.entity.ActivityLog;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.repository.ActivityLogRepository;
import com.giftconnect.util.SessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Write side of the audit trail. Business services call log(...) after
 * important actions; failures to LOG must never break the business flow,
 * so every write is wrapped and only logged to the server log.
 *
 * App-only: nothing here ever updates or deletes ActivityLog rows.
 * The caller supplies userId/role from the SERVER-SIDE session — never from
 * client input.
 */
@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository repository;

    @Autowired
    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    /** Convenience for controllers that have the request + session user. */
    public void log(ActivityType type,
                    ActivityStatus status,
                    Long userId,
                    Role role,
                    String email,
                    String entityType,
                    Long entityId,
                    String description,
                    HttpServletRequest request) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setActivityType(type);
            entry.setStatus(status);
            entry.setUserId(userId);
            entry.setUserRole(role);
            entry.setEmailSnapshot(email);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            // Single-line + width-capped: a crafted value cannot inject fake log
            // lines (CR/LF) or overflow the description column.
            entry.setDescription(sanitize(description, 500));
            entry.setIpAddress(com.giftconnect.util.IpUtils.clientIp(request));
            repository.save(entry);
        } catch (Exception e) {
            // Audit failure must not break business flow. Log server-side only.
            log.warn("Failed to write activity log entry for {}: {}", type, e.getMessage());
        }
    }

    /**
     * Request-aware overload used by services: the acting user is resolved from
     * the server-side session (never from client input), and the caller IP is
     * stored too — so catalogue/order events carry a real actor, not null.
     */
    public void log(ActivityType type,
                    ActivityStatus status,
                    HttpServletRequest request,
                    String entityType,
                    Long entityId,
                    String description) {
        SessionActor.Actor actor = SessionActor.from(request);
        log(type, status, actor.userId(), actor.role(), actor.email(),
                entityType, entityId, description, request);
    }

    /** Minimal overload used inside services where no request is at hand. */
    public void log(ActivityType type,
                    ActivityStatus status,
                    Long userId,
                    Role role,
                    String entityType,
                    Long entityId,
                    String description) {
        log(type, status, userId, role, null, entityType, entityId, description, null);
    }

    // ------------------------------------------------------------------
    // Read side (ADMIN ONLY — authorization enforced in AdminController)
    // ------------------------------------------------------------------

    public Page<ActivityLog> getFilteredLogs(Long userId,
                                             Role role,
                                             ActivityType type,
                                             String entityType,
                                             ActivityStatus status,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             int page,
                                             int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return repository.findByFilters(userId, role, type, entityType, status, from, to, pageable);
    }

    public Page<ActivityLog> getRecentAuthEvents(int count) {
        List<ActivityType> authTypes = List.of(
                ActivityType.AUTH_LOGIN,
                ActivityType.AUTH_LOGIN_FAILED,
                ActivityType.AUTH_LOGOUT,
                ActivityType.AUTH_REGISTER,
                ActivityType.AUTH_OTP_SENT,
                ActivityType.AUTH_OTP_VERIFIED,
                ActivityType.AUTH_OTP_FAILED,
                ActivityType.AUTH_PASSWORD_CHANGED
        );
        return repository.findByActivityTypeInOrderByCreatedAtDesc(authTypes, PageRequest.of(0, count));
    }

    public Page<ActivityLog> getRecentLogs(int count) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, count));
    }

    /** Total number of audit rows — used by the admin system overview. */
    public long countAllLogs() {
        return repository.count();
    }

    /**
     * Count of FAILED authentication events (bad password / bad OTP). A security
     * signal for the dashboard; the count comes from the database, not from
     * loading rows into memory.
     */
    public long countFailedAuthEvents() {
        List<ActivityType> failedAuthTypes = List.of(
                ActivityType.AUTH_LOGIN_FAILED,
                ActivityType.AUTH_OTP_FAILED
        );
        return repository.findByActivityTypeInOrderByCreatedAtDesc(failedAuthTypes, PageRequest.of(0, 1))
                .getTotalElements();
    }

    /**
     * Keeps audit text single-line and within the column width: CR/LF are folded
     * to spaces (so a crafted value cannot inject fake log lines) and the result
     * is capped at the description column size.
     */
    private String sanitize(String value, int max) {
        if (value == null) return null;
        String cleaned = value.replace('\r', ' ').replace('\n', ' ');
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}