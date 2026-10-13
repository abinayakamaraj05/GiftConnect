package com.giftconnect.dto;

import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;

import java.time.LocalDateTime;

/**
 * Safe, read-only projection of a User for API responses.
 *
 * WHY THIS EXISTS: the User entity carries credential material — the BCrypt
 * password hash and the BCrypt OTP hash plus its expiry/attempt/cooldown
 * fields. Serializing the entity would expose those, so every user-facing
 * endpoint returns this DTO instead. It simply has no such properties, which
 * makes the leak impossible rather than merely unlikely.
 */
public class UserResponse {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Role role;
    private AccountStatus accountStatus;
    private Boolean emailVerified;
    private LocalDateTime createdAt;

    public static UserResponse fromEntity(User user) {
        UserResponse response = new UserResponse();
        response.userId = user.getUserId();
        response.name = user.getName();
        response.email = user.getEmail();
        response.phone = user.getPhone();
        response.address = user.getAddress();
        response.role = user.getRole();
        response.accountStatus = user.getAccountStatus();
        response.emailVerified = user.getEmailVerified();
        response.createdAt = user.getCreatedAt();
        return response;
    }

    public Long getUserId() { return userId; }

    public String getName() { return name; }

    public String getEmail() { return email; }

    public String getPhone() { return phone; }

    public String getAddress() { return address; }

    public Role getRole() { return role; }

    public AccountStatus getAccountStatus() { return accountStatus; }

    public Boolean getEmailVerified() { return emailVerified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
