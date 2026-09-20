package com.giftconnect.controller;

import com.giftconnect.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * REST endpoints for user lookup.
 *
 * Registration and login moved to AuthController (/api/auth/register, /api/auth/login)
 * as of Week 1 — Authentication, so there's a single source of truth for account creation.
 *
 * GET  /api/users            -> list all users (ADMIN only)
 * GET  /api/users/{id}       -> get one user by id (ADMIN: any user; CUSTOMER/SELLER: own record only)
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
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
        return ResponseEntity.ok(userService.getAllUsers());
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
            return ResponseEntity.ok(user);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
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
