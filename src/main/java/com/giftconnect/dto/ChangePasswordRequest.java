package com.giftconnect.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/change-password.
 *
 * Deliberately carries ONLY the two secrets a password change needs:
 * - there is NO user id, because the target account is always the one in the
 *   authenticated server-side session (a client can never pick a victim);
 * - there is NO role, status, or email field, because a password change must
 *   never be able to alter them.
 *
 * Both values are used once, in memory: the current password to verify against
 * the stored BCrypt hash, and the new one to hash. They are never logged, never
 * persisted in this form, and never returned by any API.
 */
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
