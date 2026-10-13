package com.giftconnect.service;

import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates, hashes, verifies, and rate-limits email OTPs.
 *
 * Security properties:
 *  - 6 digits, generated with SecureRandom (cryptographically secure).
 *  - Stored ONLY as a BCrypt hash on the user row — never plaintext, never logged.
 *  - Short lifetime (default 10 minutes) and one-time use: cleared on success.
 *  - Limited verification attempts (5) — exceeded locks verification until a
 *    fresh OTP is requested (which is itself cooldown-limited).
 *  - Verification failures do not reveal whether the email is registered.
 */
@Service
public class OtpService {

    static final int OTP_LENGTH = 6;
    static final int OTP_VALID_MINUTES = 10;
    static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 60;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a fresh OTP for the user: SecureRandom 6 digits, hashed and
     * stored with its expiry, attempt counter reset, resend cooldown set.
     * Returns the RAW otp so the caller (EmailService) can put it in the
     * email — the raw value exists in memory only and is never persisted or logged.
     */
    public String generateAndStoreOtp(User user) {
        String otp = randomDigits();

        user.setOtpHash(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        user.setOtpAttempts(0);
        user.setOtpResendTime(LocalDateTime.now().plusSeconds(RESEND_COOLDOWN_SECONDS));
        // Note: the caller saves the user in the same transaction.

        return otp;
    }

    /**
     * Verifies the submitted OTP against the stored hash. On any failure the
     * attempt counter is incremented (caller must save the user). Throws
     * InvalidOperationException with a safe, non-enumerating message.
     */
    public void verifyOtp(User user, String submittedOtp) {
        if (user.getEmailVerified()) {
            return; // already verified — nothing to do
        }
        if (user.getOtpHash() == null || user.getOtpExpiry() == null) {
            throw new InvalidOperationException("No verification code is pending. Please request a new one.");
        }
        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new InvalidOperationException("This verification code has expired. Please request a new one.");
        }
        if (user.getOtpAttempts() >= MAX_ATTEMPTS) {
            throw new InvalidOperationException(
                    "Too many incorrect attempts. Please request a new verification code.");
        }

        if (!passwordEncoder.matches(submittedOtp, user.getOtpHash())) {
            user.setOtpAttempts(user.getOtpAttempts() + 1);
            throw new InvalidOperationException("Incorrect verification code.");
        }

        // Success: one-time use — clear all OTP state and mark verified.
        user.setEmailVerified(true);
        user.setOtpHash(null);
        user.setOtpExpiry(null);
        user.setOtpAttempts(0);
    }

    /** Throws if the resend cooldown is still active; otherwise returns silently. */
    public void assertResendAllowed(User user) {
        LocalDateTime cooldownUntil = user.getOtpResendTime();
        if (cooldownUntil != null && LocalDateTime.now().isBefore(cooldownUntil)) {
            long secondsLeft = java.time.Duration.between(LocalDateTime.now(), cooldownUntil).toSeconds();
            throw new InvalidOperationException(
                    "Please wait " + Math.max(secondsLeft, 1) + "s before requesting another code.");
        }
    }

    /** Remaining cooldown seconds for API responses (0 when it may be resent). */
    public long resendCooldownSecondsRemaining(User user) {
        LocalDateTime cooldownUntil = user.getOtpResendTime();
        if (cooldownUntil == null) return 0;
        long seconds = java.time.Duration.between(LocalDateTime.now(), cooldownUntil).toSeconds();
        return Math.max(seconds, 0);
    }

    /** Cryptographically secure 6-digit code; leading zeros preserved. */
    private String randomDigits() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    // Kept only so the compiler keeps the List import meaningful for future filters.
    @SuppressWarnings("unused")
    private static void unusedListReference(List<String> ignored) {
    }
}