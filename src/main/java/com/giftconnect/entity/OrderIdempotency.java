package com.giftconnect.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Phase 4I — records that a client's order-creation attempt (identified by an
 * optional {@code Idempotency-Key} request header) has already been turned into
 * exactly one Order.
 *
 * The pair (user_id, idempotency_key) is UNIQUE, so a retried or concurrent
 * duplicate request cannot create a second order for the same key: the database
 * constraint is the single source of truth. A matching replay is answered with
 * the original order (looked up via {@link #getOrder()}); a reuse of the same
 * key with different order contents is a conflict.
 *
 * The row is written in the SAME transaction as the order (and its stock
 * changes), so a failed/rolled-back order never leaves an orphaned key.
 */
@Entity
@Table(name = "order_idempotency",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_order_idempotency_user_key",
                columnNames = {"user_id", "idempotency_key"}))
public class OrderIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idempotency_id")
    private Long idempotencyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The opaque client-supplied key. Bounded to 100 chars by validation. */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** SHA-256 (hex, lowercase) of the canonical order input this key was used for. */
    @Column(name = "fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    private String fingerprint;

    /** The order that was created for this key. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderIdempotency() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ----- Getters and setters -----

    public Long getIdempotencyId() { return idempotencyId; }
    public void setIdempotencyId(Long idempotencyId) { this.idempotencyId = idempotencyId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
