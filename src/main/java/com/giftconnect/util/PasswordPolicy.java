package com.giftconnect.util;

import java.util.regex.Pattern;

/**
 * Server-side password strength policy — the single source of truth for
 * what counts as an acceptable password. The frontend mirrors these rules
 * for instant feedback, but the backend ALWAYS re-validates independently
 * (the frontend can never be trusted).
 *
 * Requirements:
 *   - minimum 8 characters
 *   - at least 1 uppercase letter
 *   - at least 1 lowercase letter
 *   - at least 1 digit
 *   - at least 1 special character (non-alphanumeric)
 *
 * Used by UserService during registration (and reusable for any future
 * password-change flow). The raw password is never logged or persisted —
 * only BCrypt hashes of it are stored (see PasswordConfig).
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {
        // static utility — no instances
    }

    /** Returns true only if the raw password satisfies every rule above. */
    public static boolean isValid(String rawPassword) {
        return violations(rawPassword).isEmpty();
    }

    /**
     * Human-readable list of rule violations, empty when valid. Returned to
     * the client so the user knows exactly which rule failed — but the
     * password value itself is never included in any message.
     */
    public static java.util.List<String> violations(String rawPassword) {
        java.util.List<String> problems = new java.util.ArrayList<>();

        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (rawPassword == null || !UPPERCASE.matcher(rawPassword).find()) {
            problems.add("Password must contain at least 1 uppercase letter");
        }
        if (rawPassword == null || !LOWERCASE.matcher(rawPassword).find()) {
            problems.add("Password must contain at least 1 lowercase letter");
        }
        if (rawPassword == null || !DIGIT.matcher(rawPassword).find()) {
            problems.add("Password must contain at least 1 number");
        }
        if (rawPassword == null || !SPECIAL.matcher(rawPassword).find()) {
            problems.add("Password must contain at least 1 special character");
        }
        return problems;
    }

    /**
     * Rough strength estimate for the frontend indicator, using the same rules.
     * 0 = weak, 1 = medium, 2 = strong. Backend authorization never depends on
     * this — it is purely cosmetic feedback mirrored on the client.
     */
    public static int strengthScore(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) return 0;
        if (!isValid(rawPassword)) return 0;

        int score = 1; // all minimum rules met
        if (rawPassword.length() >= 12) score++;
        if (rawPassword.length() >= 16) score++;
        return Math.min(score, 2);
    }
}