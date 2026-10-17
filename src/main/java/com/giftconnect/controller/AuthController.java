package com.giftconnect.controller;

import com.giftconnect.dto.ChangePasswordRequest;
import com.giftconnect.dto.OtpVerifyRequest;
import com.giftconnect.dto.RegisterRequest;
import com.giftconnect.dto.ResendOtpRequest;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.EmailService;
import com.giftconnect.service.OtpService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.SessionActor;
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
 * - On successful login we store userId + email + role in HttpSession.
 * - AuthFilter checks for that session on protected requests.
 * - No JWT, no OAuth, no Spring Security filter chain.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Session attribute keys — used here and read by AuthFilter
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USER_EMAIL = "userEmail";
    // Role is always the one loaded from the DB at login — never taken from the request body.
    public static final String SESSION_USER_ROLE = "userRole";

    private final UserService userService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    @Autowired
    public AuthController(UserService userService, OtpService otpService, EmailService emailService,
                          ActivityLogService activityLogService) {
        this.userService = userService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        // 1) Server-side password strength validation (independent of frontend).
        java.util.List<String> passwordProblems =
                com.giftconnect.util.PasswordPolicy.violations(request.getPassword());
        if (!passwordProblems.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", String.join("; ", passwordProblems)));
        }

        // 2) Never reveal whether the email already exists (anti-enumeration):
        //    unknown email  -> create account + send OTP, neutral message
        //    known unverified -> regenerate OTP + resend,        neutral message
        //    known verified  -> same neutral message, NO email sent
        User user = userService.findByEmail(request.getEmail()).orElse(null);
        String email = request.getEmail().trim().toLowerCase();

        boolean accountCreated = false;
        if (user == null) {
            try {
                user = userService.registerWithOtp(request);
            } catch (IllegalStateException e) {
                // Duplicate email — respond with the SAME neutral body as success.
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "message", "If this email is not already verified, a verification code has been sent.",
                        "email", email));
            }
            // Phase 4O: a NEW account must actually receive an OTP. Previously
            // this branch returned a "code was sent" message but never generated
            // or emailed one. Mirrors the unverified re-registration branch below:
            // generateAndStoreOtp mutates the user, then save persists the OTP fields.
            emailService.sendOtpEmail(user.getEmail(),
                    otpService.generateAndStoreOtp(user), user.getName());
            userService.save(user);
            accountCreated = true;
        } else if (user.getEmailVerified()) {
            // Existing verified account: identical neutral response, no email, no OTP churn.
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "message", "If this email is not already verified, a verification code has been sent.",
                    "email", email));
        } else {
            // Unverified account re-registering: cooldown-limited OTP re-issue.
            otpService.assertResendAllowed(user);
            emailService.sendOtpEmail(user.getEmail(), otpService.generateAndStoreOtp(user), user.getName());
            userService.save(user);
            activityLogService.log(ActivityType.AUTH_OTP_SENT, ActivityStatus.SUCCESS,
                    user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                    "Verification code re-sent (re-registration)", httpRequest);
        }

        if (accountCreated) {
            activityLogService.log(ActivityType.AUTH_REGISTER, ActivityStatus.SUCCESS,
                    user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                    "Account registered (pending email verification)", httpRequest);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", accountCreated
                        ? "Account created. A 6-digit verification code was sent to your email (expires in 10 minutes)."
                        : "If this email is not already verified, a verification code has been sent.",
                "email", email));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                       HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();

        // Unknown email gets the SAME response as a wrong OTP — no enumeration.
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid or expired verification code."));
        }

        try {
            otpService.verifyOtp(user, request.getOtp());
        } catch (InvalidOperationException e) {
            userService.save(user); // persist the incremented attempt counter
            activityLogService.log(ActivityType.AUTH_OTP_FAILED, ActivityStatus.FAILURE,
                    user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                    "OTP verification failed: " + e.getMessage(), httpRequest);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }

        userService.save(user); // persist verified = true + cleared OTP state
        activityLogService.log(ActivityType.AUTH_OTP_VERIFIED, ActivityStatus.SUCCESS,
                user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                "Email verified via OTP", httpRequest);
        return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully. You can now log in.",
                "email", email));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequest request,
                                       HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userService.findByEmail(email).orElse(null);

        // Neutral response for unknown or already-verified emails — no enumeration.
        if (user == null || user.getEmailVerified()) {
            return ResponseEntity.ok(Map.of(
                    "message", "If this email needs verification, a new code has been sent.",
                    "resendCooldownSeconds", OtpService.RESEND_COOLDOWN_SECONDS));
        }

        long cooldown = otpService.resendCooldownSecondsRemaining(user);
        if (cooldown > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Please wait " + cooldown + "s before requesting another code.",
                    "resendCooldownSeconds", cooldown));
        }

        emailService.sendOtpEmail(user.getEmail(), otpService.generateAndStoreOtp(user), user.getName());
        userService.save(user);
        activityLogService.log(ActivityType.AUTH_OTP_SENT, ActivityStatus.SUCCESS,
                user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                "Verification code re-sent (resend endpoint)", httpRequest);

        return ResponseEntity.ok(Map.of(
                "message", "A new verification code has been sent (expires in 10 minutes).",
                "resendCooldownSeconds", OtpService.RESEND_COOLDOWN_SECONDS));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Optional<User> matchedUser = userService.authenticate(request.getEmail(), request.getPassword());

        if (matchedUser.isEmpty()) {
            // Audit: failed login (never logs the attempted password).
            activityLogService.log(ActivityType.AUTH_LOGIN_FAILED, ActivityStatus.FAILURE,
                    null, null, request.getEmail(), "USER", null,
                    "Failed login attempt", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        User user = matchedUser.get();

        // Phase 5: a DEACTIVATED account cannot log in. Nothing was deleted — an
        // administrator can reactivate it and the account, its orders, and its
        // payment history are exactly as they were.
        if (!user.isActive()) {
            activityLogService.log(ActivityType.AUTH_LOGIN_FAILED, ActivityStatus.FAILURE,
                    user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                    "Login blocked — account is " + user.getAccountStatus(), httpRequest);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This account has been deactivated. Please contact support."));
        }

        // Unverified accounts cannot log in until OTP verification succeeds.
        if (!user.getEmailVerified()) {
            activityLogService.log(ActivityType.AUTH_LOGIN_FAILED, ActivityStatus.FAILURE,
                    user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                    "Login blocked — email not verified", httpRequest);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Email not verified. Enter the 6-digit code we emailed you.",
                    "email", user.getEmail(),
                    "needsVerification", true));
        }

        // Audit: successful login.
        activityLogService.log(ActivityType.AUTH_LOGIN, ActivityStatus.SUCCESS,
                user.getUserId(), user.getRole(), user.getEmail(), "USER", user.getUserId(),
                "User logged in", httpRequest);

        // Create a new session (or reuse the current one) and store identity in it.
        // The role comes from the authenticated User loaded by the backend — the login
        // request body has no role field, and any role sent by the client is ignored.
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getUserId());
        session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
        session.setAttribute(SESSION_USER_ROLE, user.getRole());

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "userId", user.getUserId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()
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

        // Role shown here was re-stamped from the database by AuthFilter on this
        // very request (Phase 4Q), so an ADMIN role change is already reflected —
        // no re-login needed. For anonymous/legacy sessions it still falls back to
        // the entity default instead of failing; Map.of() rejects null values.
        Object role = session.getAttribute(SESSION_USER_ROLE);
        if (role == null) {
            role = Role.CUSTOMER;
        }

        // Enrich /me with name + verification status so the frontend can render
        // the role-aware navigation and block unverified accounts. Looked up from
        // the DB, keyed by the session's own userId — never from client input.
        String name = null;
        boolean emailVerified = true;
        AccountStatus accountStatus = AccountStatus.ACTIVE;
        Object sessionUserId = session.getAttribute(SESSION_USER_ID);
        if (sessionUserId instanceof Number n) {
            java.util.Optional<User> dbUser = userService.getUserByIdOptional(n.longValue());
            if (dbUser.isPresent()) {
                name = dbUser.get().getName();
                emailVerified = dbUser.get().getEmailVerified();
                accountStatus = dbUser.get().getAccountStatus();
            }
        }

        return ResponseEntity.ok(Map.of(
                "userId", session.getAttribute(SESSION_USER_ID),
                "email", session.getAttribute(SESSION_USER_EMAIL),
                "name", name != null ? name : "",
                "emailVerified", emailVerified,
                "accountStatus", accountStatus,
                "role", role
        ));
    }

    /**
     * Self-service password change for the account behind THIS session.
     *
     * Identity: taken exclusively from the server-side session via SessionActor —
     * the request body has no user id field, so a caller can only ever change
     * their own password. AuthFilter has already required a session and rejected
     * a deactivated/removed account before this runs.
     *
     * Errors: a wrong current password, a weak new password, or re-using the
     * current one are all business-rule violations and become 400 responses via
     * GlobalExceptionHandler. The success audit event is written ONLY after the
     * service call returns, so a failure never records a misleading success.
     *
     * The session is deliberately kept alive (same as before) — no role, status
     * or identity changes, and nothing about the password is echoed back.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        SessionActor.Actor actor = SessionActor.from(httpRequest);
        if (actor.userId() == null) {
            // AuthFilter normally guarantees this; kept as a fail-closed guard.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not logged in"));
        }

        userService.changePassword(actor.userId(), request.getCurrentPassword(), request.getNewPassword());

        activityLogService.log(ActivityType.AUTH_PASSWORD_CHANGED, ActivityStatus.SUCCESS,
                actor.userId(), actor.role(), actor.email(), "USER", actor.userId(),
                "Password changed", httpRequest);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            // Audit before invalidation — identity is gone afterwards.
            SessionActor.Actor actor = SessionActor.from(httpRequest);
            if (actor.userId() != null) {
                activityLogService.log(ActivityType.AUTH_LOGOUT, ActivityStatus.SUCCESS,
                        actor.userId(), actor.role(), actor.email(),
                        "USER", actor.userId(), "User logged out", httpRequest);
            }
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
