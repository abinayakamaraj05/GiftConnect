package com.giftconnect.dto;

import com.giftconnect.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/admin/users/{id}/role.
 *
 * The role is validated against the existing Role enum, so no new role can be
 * introduced from the client. An unknown value (e.g. "MANAGER") is rejected
 * with 400 by GlobalExceptionHandler.
 */
public class UserRoleUpdateRequest {

    @NotNull(message = "Role is required")
    private Role role;

    public Role getRole() { return role; }

    public void setRole(Role role) { this.role = role; }
}
