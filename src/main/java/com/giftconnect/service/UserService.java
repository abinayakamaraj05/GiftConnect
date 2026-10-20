package com.giftconnect.service;

import com.giftconnect.dto.ProfileUpdateRequest;
import com.giftconnect.dto.UserResponse;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.util.PasswordPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Business logic for user registration, login, and lookup.
 * Controllers should never talk to the repository directly — they go through this service.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * A valid throwaway BCrypt hash used to burn the same CPU time when the
     * email does not exist as when a real account's password is compared. This
     * flattens the login timing difference that would otherwise let an attacker
     * tell registered emails from unknown ones. Generated once at construction
     * so the work cost is realistic and no constant hash is committed.
     */
    private final String dummyPasswordHash;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("giftconnect-timing-equalizer");
    }

    /**
     * Registers a new user after checking that the email isn't already taken.
     * The plaintext password is hashed with BCrypt before it's ever saved —
     * the raw password is never persisted or logged.
     *
     * Public self-registration ALWAYS creates a CUSTOMER. Any role supplied in
     * the request body is ignored and overwritten here, so a client can never
     * self-register as SELLER or ADMIN. Privileged accounts must be created
     * through a separate, trusted (non-public) path.
     *
     * Throws IllegalStateException if the email is a duplicate — the controller
     * catches this and returns HTTP 409 Conflict.
     */
    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalStateException("Email already registered: " + user.getEmail());
        }
        user.setRole(Role.CUSTOMER);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    /**
     * Phase 1: OTP-based registration. Validates the password policy
     * server-side (independent of any frontend check) and creates the account
     * in an UNVERIFIED state. It does NOT generate, store, or send the OTP —
     * since the Phase 4O fix, that is owned by AuthController.register's
     * new-account branch (mirroring the unverified re-registration branch).
     *
     * The response to the client is neutral — it never reveals whether an
     * email is already registered when the caller is unauthenticated.
     */
    public User registerWithOtp(com.giftconnect.dto.RegisterRequest request) {
        // Server-side password strength validation — never trust the frontend.
        java.util.List<String> passwordProblems =
                com.giftconnect.util.PasswordPolicy.violations(request.getPassword());
        if (!passwordProblems.isEmpty()) {
            throw new InvalidOperationException(String.join("; ", passwordProblems));
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setRole(Role.CUSTOMER); // public registration is always CUSTOMER
        user.setEmailVerified(false);
        user.setPassword(passwordEncoder.encode(request.getPassword())); // BCrypt immediately

        // Duplicate email -> 409 via IllegalStateException (unchanged behavior).
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalStateException("Email already registered: " + user.getEmail());
        }

        User saved = userRepository.save(user);
        return saved;
    }

    /**
     * Marks the account verified after OtpService.verifyOtp succeeded.
     * Kept separate so the transaction boundary is explicit at the controller.
     */
    public User save(User user) {
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email == null ? null : email.trim().toLowerCase());
    }

    /**
     * Verifies login credentials. Returns the matching User if the email exists
     * AND the given plaintext password matches the stored hash. Returns
     * Optional.empty() for either a missing user or a wrong password — the
     * controller treats both cases identically (401) so we don't leak which
     * one was wrong.
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        // Normalize exactly like registration (trim + lowercase) so a login with
        // different casing/whitespace still finds the account, and look up via the
        // service method so there is a single normalization rule in one place.
        Optional<User> found = findByEmail(email);

        if (found.isEmpty()) {
            // Equalize timing with the wrong-password path: run a real BCrypt
            // comparison against a throwaway hash so an unknown email takes the
            // same time as a known email with the wrong password.
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyPasswordHash);
            return Optional.empty();
        }

        return found.filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Throws NoSuchElementException if no user exists with the given id —
     * the controller catches this and returns HTTP 404 Not Found.
     */
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
    }

    /** Non-throwing variant used by AuthController /me enrichment. */
    public java.util.Optional<User> getUserByIdOptional(Long id) {
        return userRepository.findById(id);
    }

    // ==================================================================
    // Phase: secure account management (self-service password + profile)
    // ==================================================================

    /**
     * Self-service password change for the account identified by {@code userId}.
     *
     * The controller passes ONLY the id it read from the authenticated session,
     * so a caller can never aim this at somebody else's account.
     *
     * Checks, in order:
     *   1. the account exists (404 if it does not — it is the caller's own id,
     *      so this reveals nothing about anyone else);
     *   2. the account is still ACTIVE (a deactivated account cannot rotate
     *      its credentials even if it races AuthFilter's own check);
     *   3. the supplied current password matches the stored BCrypt hash;
     *   4. the new password satisfies {@link PasswordPolicy} — the SAME policy
     *      registration uses, never a competing one;
     *   5. the new password differs from the current one.
     *
     * On success the new password is BCrypt-encoded before the single save, so
     * plaintext is never persisted. Any failed check throws before save, which
     * leaves the stored password untouched (no partial update) and lets the
     * caller avoid writing a misleading success audit event.
     *
     * Transactional: the encode + save happen as one unit.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Defense in depth: AuthFilter already destroys sessions for INACTIVE
        // accounts, but the credential rotation itself refuses them too.
        if (!user.isActive()) {
            throw new InvalidOperationException("This account has been deactivated. Please contact support.");
        }

        // Null-safe by construction: a null current password is rejected before
        // it ever reaches PasswordEncoder.matches (which would otherwise NPE).
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidOperationException("Current password is incorrect.");
        }

        List<String> passwordProblems = PasswordPolicy.violations(newPassword);
        if (!passwordProblems.isEmpty()) {
            throw new InvalidOperationException(String.join("; ", passwordProblems));
        }

        // Checked AFTER the policy, so a null/empty new password never reaches
        // String.equals — and a re-used password is refused with a clear reason.
        if (newPassword.equals(currentPassword)) {
            throw new InvalidOperationException("New password must be different from your current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Self-service update of the caller's own editable profile fields.
     *
     * Only name, phone and address are written: the request DTO has no other
     * setters, so email, role, account status, verification state, OTP fields
     * and the password hash are structurally impossible to change here even
     * if a client posts them.
     *
     * Returns the safe {@link UserResponse} projection, so credential material
     * can never be serialized to the client. Transactional so a failed save
     * cannot leave a half-applied update.
     */
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setName(request.getName().trim());
        // Optional fields: blank comes back as null rather than an empty string.
        user.setPhone(trimToNull(request.getPhone()));
        user.setAddress(trimToNull(request.getAddress()));

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    /** Trims a value; blank or whitespace-only becomes null. */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
