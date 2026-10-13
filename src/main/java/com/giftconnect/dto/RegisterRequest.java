package com.giftconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/register.
 *
 * Replaces the old approach of binding a raw User entity: the client can now
 * NEVER supply id, role, createdAt, or any verification/OTP field — this DTO
 * simply has no such properties, so the server decides everything important.
 *
 * NOTE: there is deliberately no `role` field. Public self-registration always
 * creates a CUSTOMER (enforced again in UserService).
 */
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /*
     * Raw (plaintext) password, transmitted over HTTPS and validated against
     * PasswordPolicy in the service, then immediately BCrypt-hashed. It is
     * never logged, never persisted in this form, and never returned by any API.
     */
    @NotBlank(message = "Password is required")
    private String password;

    @Size(max = 15, message = "Phone must be at most 15 characters")
    private String phone;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}