package com.giftconnect.dto;

import com.giftconnect.entity.ActivityLog;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;

import java.time.LocalDateTime;

/**
 * Read-only shape of one audit row, used by the ADMIN activity dashboard.
 *
 * Deliberately exposes only what an administrator needs: who acted, what they
 * did, which record it touched, the outcome, and when/from where it happened.
 * There is nothing sensitive here — no password, password hash, OTP value,
 * card number, CVV, token, or session id is ever stored in the first place
 * (see ActivityLog's design rules).
 */
public class ActivityLogResponse {

    private Long logId;
    private Long userId;
    private Role userRole;
    /** Email snapshot taken at the time of the action; null for anonymous events. */
    private String email;
    private ActivityType activityType;
    private String entityType;
    private Long entityId;
    private String description;
    private ActivityStatus status;
    private String ipAddress;
    private LocalDateTime createdAt;

    public static ActivityLogResponse fromEntity(ActivityLog entry) {
        ActivityLogResponse response = new ActivityLogResponse();
        response.logId = entry.getLogId();
        response.userId = entry.getUserId();
        response.userRole = entry.getUserRole();
        response.email = entry.getEmailSnapshot();
        response.activityType = entry.getActivityType();
        response.entityType = entry.getEntityType();
        response.entityId = entry.getEntityId();
        response.description = entry.getDescription();
        response.status = entry.getStatus();
        response.ipAddress = entry.getIpAddress();
        response.createdAt = entry.getCreatedAt();
        return response;
    }

    public Long getLogId() { return logId; }

    public Long getUserId() { return userId; }

    public Role getUserRole() { return userRole; }

    public String getEmail() { return email; }

    public ActivityType getActivityType() { return activityType; }

    public String getEntityType() { return entityType; }

    public Long getEntityId() { return entityId; }

    public String getDescription() { return description; }

    public ActivityStatus getStatus() { return status; }

    public String getIpAddress() { return ipAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
