package com.giftconnect.controller;

import com.giftconnect.entity.User;
import com.giftconnect.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Handles registration, login, session check, and logout.
 *
 * Session-based auth, kept intentionally simple for a college MVP:
 * - On successful login we store userId + email in HttpSession.
 * - AuthFilter checks for that session on protected requests.
 * - No JWT, no OAuth, no Spring Security filter chain.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Session attribute keys — used here and read by AuthFilter
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USER_EMAIL = "userEmail";

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody User user) {
        try {
            User savedUser = userService.registerUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
        } catch (IllegalStateException e) {
            // Duplicate email
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Optional<User> matchedUser = userService.authenticate(request.getEmail(), request.getPassword());

        if (matchedUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        User user = matchedUser.get();

        // Create a new session (or reuse the current one) and store identity in it.
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getUserId());
        session.setAttribute(SESSION_USER_EMAIL, user.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "userId", user.getUserId(),
                "name", user.getName(),
                "email", user.getEmail()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(HttpServletRequest httpRequest) {
        // false = do not create a new session just to check it
        HttpSession session = httpRequest.getSession(false);

        if (session == null || session.getAttribute(SESSION_USER_ID) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        return ResponseEntity.ok(Map.of(
                "userId", session.getAttribute(SESSION_USER_ID),
                "email", session.getAttribute(SESSION_USER_EMAIL)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Small request-only DTO for the login endpoint. Kept in this file rather
     * than its own class since it's only ever used here — avoids adding an
     * "unnecessary class" per the project's Day 1 ground rules.
     */
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

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
    }
}
