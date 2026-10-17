package com.giftconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/users/me — the caller's OWN profile only.
 *
 * Contains exactly the editable display fields that already exist on the User
 * entity. It has NO userId, email, role, accountStatus, emailVerified, OTP or
 * password property, so none of them can ever be bound from a request body:
 * those values simply have no setter to bind to. Identity always comes from the
 * authenticated session, never from the body or the URL.
 *
 * Email is intentionally absent too: it is the login identity and is tied to
 * OTP verification, so changing it is out of scope for this self-service flow.
 */
public class ProfileUpdateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 15, message = "Phone must be at most 15 characters")
    private String phone;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
