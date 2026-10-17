package com.giftconnect.controller;

import com.giftconnect.dto.ProfileUpdateRequest;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.giftconnect.dto.UserResponse;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * REST endpoints for user lookup.
 *
 * Registration and login moved to AuthController (/api/auth/register, /api/auth/login)
 * as of Week 1 — Authentication, so there's a single source of truth for account creation.
 *
 * GET  /api/users            -> list all users (ADMIN only)
 * GET  /api/users/{id}       -> get one user by id (ADMIN: any user; CUSTOMER/SELLER: own record only)
 * GET  /api/users/me         -> the caller's own profile (session identity, safe DTO)
 * PUT  /api/users/me         -> update the caller's own name/phone/address
 *
 * Phase 5 security fix: both GET endpoints return the existing UserResponse DTO
 * instead of the User entity, so password/OTP material can never be serialized.
 *
 * The /me endpoints are deliberately literal paths with no {id}: the target is
 * resolved from the server-side session only, so there is no id for a client to
 * aim at another account. "/api/users/me" is matched ahead of "/api/users/{id}"
 * because an exact path always beats a path variable in Spring's mapping order.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ActivityLogService activityLogService;

    @Autowired
    public UserController(UserService userService, ActivityLogService activityLogService) {
        this.userService = userService;
        this.activityLogService = activityLogService;
    }

    /**
     * ADMIN only — a non-admin must not be able to list every account.
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(HttpServletRequest request) {
        if (roleOf(request) != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only an ADMIN can list all users"));
        }
        return ResponseEntity.ok(userService.getAllUsers().stream()
                .map(UserResponse::fromEntity)
                .toList());
    }

    /**
     * ADMIN can view anyone. CUSTOMER/SELLER may only view their own record —
     * any other user's id is rejected with 403.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id, HttpServletRequest request) {
        if (roleOf(request) != Role.ADMIN && !id.equals(currentUserId(request))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You can only view your own user record"));
        }

        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(UserResponse.fromEntity(user));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * The caller's own profile, read by session identity — no id in the URL to
     * tamper with. Returns the safe UserResponse DTO (no password hash, no OTP
     * fields). Deliberately NOT audited: viewing your own profile is not a
     * security-relevant event and would only add noise to the audit trail.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        Long id = currentUserId(request);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        try {
            return ResponseEntity.ok(UserResponse.fromEntity(userService.getUserById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Updates the caller's own editable profile fields (name, phone, address).
     *
     * Identity: always the session's user id — a userId in the query string or
     * body is ignored because neither is read here. Editable fields only, since
     * ProfileUpdateRequest has no setter for email, role, status, verification
     * or password (see that DTO).
     *
     * Audit: PROFILE_UPDATED is written only after the service call returns
     * successfully, so validation failures (400) never record a success event.
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody ProfileUpdateRequest body,
                                               HttpServletRequest request) {
        Long id = currentUserId(request);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        UserResponse saved = userService.updateProfile(id, body);

        activityLogService.log(ActivityType.PROFILE_UPDATED, ActivityStatus.SUCCESS,
                request, "USER", saved.getUserId(),
                "Profile updated (name, phone, address)");

        return ResponseEntity.ok(saved);
    }

    /**
     * Role the AuthFilter resolved from the server-side session — never from a request
     * parameter, header, body, or client-provided data. Falls back to CUSTOMER, the least
     * privileged role, so a missing/unknown role can never grant extra access.
     */
    private Role roleOf(HttpServletRequest request) {
        Object role = request.getAttribute(AuthFilter.REQUEST_USER_ROLE);
        return role instanceof Role r ? r : Role.CUSTOMER;
    }

    /**
     * The authenticated caller's own user ID, read from the existing HTTP session only.
     * AuthFilter guarantees the session is authenticated before these endpoints run.
     */
    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object id = session.getAttribute(AuthController.SESSION_USER_ID);
        return id instanceof Number n ? n.longValue() : null;
    }
}
