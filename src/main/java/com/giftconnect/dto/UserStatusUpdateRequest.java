package com.giftconnect.dto;

import com.giftconnect.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/admin/users/{id}/status.
 *
 * Only the status is accepted — never the user id, the acting admin, or
 * anything else that could be used to escalate privileges. An unknown value
 * (e.g. "DELETED") is rejected with 400 by GlobalExceptionHandler.
 */
public class UserStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private AccountStatus status;

    public AccountStatus getStatus() { return status; }

    public void setStatus(AccountStatus status) { this.status = status; }
}
