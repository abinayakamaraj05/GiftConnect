package com.giftconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/resend-otp.
 *
 * Resending is rate-limited server-side (cooldown stored on the user row),
 * and the response is deliberately neutral so it cannot be used to discover
 * whether an email is registered.
 */
public class ResendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}