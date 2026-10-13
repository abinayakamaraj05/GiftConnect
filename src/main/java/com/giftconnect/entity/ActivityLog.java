package com.giftconnect.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Append-only audit trail. Maps to the "activity_logs" table.
 *
 * Design rules:
 *  - user_id / user_role are PLAIN COLUMNS, not a FK to users: log rows must
 *    survive a user being deleted, and must never cascade anywhere.
 *  - NEVER store passwords, password hashes, OTP values, card numbers, CVV,
 *    session ids or tokens in ANY field. entityType/entityId/description carry
 *    business identifiers only.
 *  - Nothing in the app ever updates or deletes these rows — services only
 *    insert. (No service exposes edit/delete for this entity.)
 */
@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    /** The acting user's id, or null for anonymous events (e.g. failed login). */
    @Column(name = "user_id")
    private Long userId;

    /** Role at the time of the event; null when anonymous. */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20)
    private Role userRole;

    /** Email snapshot at event time (useful when the account is later deleted). */
    @Column(name = "email_snapshot", length = 100)
    private String emailSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType activityType;

    /** e.g. "PRODUCT", "ORDER", "USER", "PAYMENT" — coarse target classifier. */
    @Column(name = "entity_type", length = 40)
    private String entityType;

    /** Id of the targeted entity, when applicable. */
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ActivityStatus status = ActivityStatus.SUCCESS;

    /** Client IP when safely available (X-Forwarded-For aware), else null. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ActivityLog() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ----- Getters and setters -----

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getUserRole() { return userRole; }
    public void setUserRole(Role userRole) { this.userRole = userRole; }

    public String getEmailSnapshot() { return emailSnapshot; }
    public void setEmailSnapshot(String emailSnapshot) { this.emailSnapshot = emailSnapshot; }

    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ActivityStatus getStatus() { return status; }
    public void setStatus(ActivityStatus status) { this.status = status; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}