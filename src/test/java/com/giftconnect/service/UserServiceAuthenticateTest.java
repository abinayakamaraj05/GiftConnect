package com.giftconnect.service;

import com.giftconnect.entity.User;
import com.giftconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#authenticate} covering the two hardening
 * changes: email normalization (R6) and the dummy BCrypt comparison that blunts
 * login timing-based email enumeration (R2). The encoder and repository are
 * mocks so the interactions are asserted exactly.
 */
class UserServiceAuthenticateTest {

    private static final String DUMMY_HASH = "$2a$10$placeholderplaceholderplaceholderplaceholder";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        userService = new UserService(userRepository, passwordEncoder);
    }

    private User userWithStoredHash(String email, String storedHash) {
        User user = new User();
        user.setUserId(1L);
        user.setEmail(email);
        user.setPassword(storedHash);
        return user;
    }

    @Test
    void normalizeEmail_trimsAndLowercasesBeforeLookup() {
        User user = userWithStoredHash("user@example.com", "stored-hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret1!", "stored-hash")).thenReturn(true);

        Optional<User> result = userService.authenticate("  USER@Example.COM  ", "Secret1!");

        assertTrue(result.isPresent());
        // The repository was queried with the SAME normalized form registration stores.
        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    void knownEmailWithWrongPassword_isRejected() {
        User user = userWithStoredHash("user@example.com", "stored-hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong1!", "stored-hash")).thenReturn(false);

        assertFalse(userService.authenticate("user@example.com", "Wrong1!").isPresent());
    }

    @Test
    void unknownEmail_stillPerformsAComparisonAgainstAThrowawayHash() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.authenticate("ghost@example.com", "Secret1!");

        assertTrue(result.isEmpty());
        // The timing-equalizing comparison ran against the dummy hash — the
        // unknown-email path is not a fast path anymore.
        verify(passwordEncoder).matches("Secret1!", DUMMY_HASH);
    }

    @Test
    void unknownEmail_withNullPassword_doesNotThrow() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertTrue(userService.authenticate("ghost@example.com", null).isEmpty());
    }

    @Test
    void knownEmailWithCorrectPassword_returnsTheUser() {
        User user = userWithStoredHash("user@example.com", "stored-hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret1!", "stored-hash")).thenReturn(true);

        Optional<User> result = userService.authenticate("user@example.com", "Secret1!");

        assertTrue(result.isPresent());
        assertSame(user, result.get());
    }
}
