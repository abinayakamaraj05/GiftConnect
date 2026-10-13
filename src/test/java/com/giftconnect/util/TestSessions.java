package com.giftconnect.util;

import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.UserService;

import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * Test-only helpers for @WebMvcTest slices that exercise the REAL AuthFilter.
 *
 * Phase 4Q made AuthFilter re-validate a session's role/status against
 * {@link UserService#getUserByIdOptional} on every protected request and refresh
 * the session from it, so every session-based test now also has to say what the
 * authoritative database row looks like. These helpers keep that compact and
 * consistent across the controller test classes; nothing here touches a real
 * database.
 */
public final class TestSessions {

    private TestSessions() {
        // static helper — no instances
    }

    /** An ACTIVE user row the filter will accept. */
    public static User activeUser(Long id, Role role) {
        return user(id, role, AccountStatus.ACTIVE);
    }

    /** A user row with an explicit status (use INACTIVE to simulate deactivation). */
    public static User user(Long id, Role role, AccountStatus status) {
        User user = new User();
        user.setUserId(id);
        user.setEmail("actor@example.com");
        user.setName("Test Actor");
        user.setRole(role);
        user.setAccountStatus(status);
        user.setEmailVerified(true);
        return user;
    }

    /** Points getUserByIdOptional(id) at an ACTIVE row carrying the given role. */
    public static void stubActiveSession(UserService userService, Long id, Role role) {
        when(userService.getUserByIdOptional(id)).thenReturn(Optional.of(activeUser(id, role)));
    }
}