package com.giftconnect.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @NotBlank(message = "Name is required")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.CUSTOMER;

    /**
     * Phase 5 (ADMIN user management): whether the account may be used.
     *
     * ACTIVE by default, and the additive column is created with
     * DEFAULT 'ACTIVE', so pre-existing rows keep working untouched.
     * Deactivating an account never removes the user or any related record.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    // ------------------------------------------------------------------
    // Email verification / OTP (Phase 1 — additive columns, nothing dropped)
    // ------------------------------------------------------------------

    /** True only after the user proves ownership of the email via OTP. */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    /**
     * BCrypt hash of the current 6-digit OTP — the OTP value itself is NEVER
     * stored in plaintext. Null when no OTP is pending.
     */
    @Column(name = "otp_hash", length = 100)
    private String otpHash;

    /** When the current OTP stops being accepted. */
    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    /** Failed verification attempts against the current OTP. */
    @Column(name = "otp_attempts")
    private Integer otpAttempts = 0;

    /** Earliest time another OTP may be sent (resend cooldown). */
    @Column(name = "otp_resend_time")
    private LocalDateTime otpResendTime;

    // ------------------------------------------------------------------

    public Boolean getEmailVerified() {
        return emailVerified != null && emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified != null && emailVerified;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getOtpExpiry() {
        return otpExpiry;
    }

    public void setOtpExpiry(LocalDateTime otpExpiry) {
        this.otpExpiry = otpExpiry;
    }

    public Integer getOtpAttempts() {
        return otpAttempts == null ? 0 : otpAttempts;
    }

    public void setOtpAttempts(Integer otpAttempts) {
        this.otpAttempts = otpAttempts == null ? 0 : otpAttempts;
    }

    public LocalDateTime getOtpResendTime() {
        return otpResendTime;
    }

    public void setOtpResendTime(LocalDateTime otpResendTime) {
        this.otpResendTime = otpResendTime;
    }

    public User() {
        // Required no-arg constructor for JPA
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.role == null) {
            this.role = Role.CUSTOMER;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    // ------------------------------------------------------------------
    // Phase 5 (ADMIN user management) — account status
    // ------------------------------------------------------------------

    /** Never returns null: an unexpected null (a row written outside the app) means ACTIVE. */
    public AccountStatus getAccountStatus() {
        return accountStatus == null ? AccountStatus.ACTIVE : accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus == null ? AccountStatus.ACTIVE : accountStatus;
    }

    /** True only for an ACTIVE account. Login requires this. */
    public boolean isActive() {
        return getAccountStatus() == AccountStatus.ACTIVE;
    }
}