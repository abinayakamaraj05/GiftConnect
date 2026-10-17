package com.giftconnect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftconnect.dto.ProfileUpdateRequest;
import com.giftconnect.dto.UserResponse;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the two self-service account operations added to UserService:
 * changePassword(...) and updateProfile(...).
 *
 * A REAL BCryptPasswordEncoder is used (the same bean PasswordConfig exposes at
 * runtime) so the "stored value is a hash, and the new password actually
 * verifies through the encoder" guarantees are tested for real. Only the
 * repository is mocked — no database, no mail, no payment, nothing external.
 */
class UserServiceAccountTest {

    private static final Long USER_ID = 5L;
    private static final String OLD_PASSWORD = "OldPass1!";
    private static final String NEW_PASSWORD = "BrandNew1!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);

        // save(...) returns the same entity, exactly like JPA's managed flush.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** An ACTIVE user whose stored password is a real BCrypt hash of OLD_PASSWORD. */
    private User activeUser() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setName("Riya");
        user.setEmail("riya@example.com");
        user.setPhone("9999999999");
        user.setAddress("Mumbai");
        user.setRole(Role.CUSTOMER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        return user;
    }

    private User stubUser(User user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    // ==================================================================
    // changePassword
    // ==================================================================

    @Test
    void changePassword_persistsBcryptHashThatVerifiesOnlyForTheNewPassword() {
        User user = stubUser(activeUser());

        userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        String stored = captor.getValue().getPassword();

        // Encoded, never plaintext — and it is the NEW password that verifies.
        assertNotEquals(NEW_PASSWORD, stored);
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash, got: " + stored);
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, stored));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, stored));
        // Nothing else about the account moved.
        assertEquals(user.getEmail(), captor.getValue().getEmail());
        assertEquals(Role.CUSTOMER, captor.getValue().getRole());
        assertTrue(user.getEmailVerified());
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsAndLeavesTheStoredHashUntouched() {
        User user = stubUser(activeUser());
        String hashBefore = user.getPassword();

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, "NotTheRight1!", NEW_PASSWORD));

        assertEquals("Current password is incorrect.", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
        assertEquals(hashBefore, user.getPassword());
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
    }

    @Test
    void changePassword_weakNewPassword_throwsAndNeverSaves() {
        User user = stubUser(activeUser());

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, "short"));

        assertTrue(error.getMessage().contains("at least 8 characters"));
        verify(userRepository, never()).save(any(User.class));
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
    }

    @Test
    void changePassword_reusingTheCurrentPassword_isRejected() {
        User user = stubUser(activeUser());

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, OLD_PASSWORD));

        assertEquals("New password must be different from your current password.", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
    }

    @Test
    void changePassword_nullCurrentPassword_isRejectedInsteadOfThrowingAnNpe() {
        stubUser(activeUser());

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, null, NEW_PASSWORD));

        assertEquals("Current password is incorrect.", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_nullNewPassword_isRejectedByThePolicy() {
        stubUser(activeUser());

        assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, null));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_deactivatedAccount_isRejectedEvenWithTheRightPassword() {
        User user = activeUser();
        user.setAccountStatus(AccountStatus.INACTIVE);
        stubUser(user);

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD));

        assertTrue(error.getMessage().contains("deactivated"));
        verify(userRepository, never()).save(any(User.class));
        assertTrue(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
    }

    @Test
    void changePassword_unknownUser_throwsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.changePassword(USER_ID, OLD_PASSWORD, NEW_PASSWORD));
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================================================================
    // updateProfile
    // ==================================================================

    @Test
    void updateProfile_changesOnlyTheEditableFields() {
        User user = stubUser(activeUser());
        String hashBefore = user.getPassword();
        user.setOtpHash("$2a$10$otpMaterialThatMustNeverChange000000000000000000000");

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("  Riya Sharma  "); // the service trims before persisting
        request.setPhone(" 9876543210 ");
        request.setAddress("  Chennai  ");

        UserResponse response = userService.updateProfile(USER_ID, request);

        assertEquals("Riya Sharma", user.getName());
        assertEquals("9876543210", user.getPhone());
        assertEquals("Chennai", user.getAddress());

        // Everything privileged/verification-related is untouched.
        assertEquals("riya@example.com", user.getEmail());
        assertEquals(Role.CUSTOMER, user.getRole());
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        assertTrue(user.getEmailVerified());
        assertEquals(hashBefore, user.getPassword());
        assertEquals("$2a$10$otpMaterialThatMustNeverChange000000000000000000000", user.getOtpHash());
        assertNull(user.getOtpExpiry());

        // The response mirrors the same safe state.
        assertEquals(USER_ID, response.getUserId());
        assertEquals("Riya Sharma", response.getName());
        assertEquals("riya@example.com", response.getEmail());
    }

    @Test
    void updateProfile_blankOptionalFieldsAreStoredAsNull() {
        User user = stubUser(activeUser());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("Riya");
        request.setPhone("   ");
        request.setAddress("");

        userService.updateProfile(USER_ID, request);

        assertNull(user.getPhone());
        assertNull(user.getAddress());
    }

    @Test
    void updateProfile_returnsDtoWithNoCredentialOrOtpMaterial() throws Exception {
        stubUser(activeUser());
        User user = userRepository.findById(USER_ID).orElseThrow();
        user.setOtpHash("$2a$10$otpHashThatMustNotBeSerialized00000000000000000000");

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("Riya");
        UserResponse response = userService.updateProfile(USER_ID, request);

        String json = new ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains("password"), "response leaked a password field: " + json);
        assertFalse(json.toLowerCase().contains("otp"), "response leaked OTP material: " + json);
        assertFalse(json.contains("$2a$"), "response leaked a hash: " + json);
    }

    @Test
    void updateProfile_unknownUser_throwsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("Riya");

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfile(USER_ID, request));
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================================================================
    // Structural guarantees: the DTOs cannot express a privileged change
    // ==================================================================

    @Test
    void profileRequest_exposesSettersForTheEditableFieldsOnly() {
        Set<String> setters = Arrays.stream(ProfileUpdateRequest.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("set"))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("setName", "setPhone", "setAddress"), setters);
        // Explicitly: none of these can be bound from a request body.
        assertFalse(setters.contains("setUserId"));
        assertFalse(setters.contains("setEmail"));
        assertFalse(setters.contains("setRole"));
        assertFalse(setters.contains("setAccountStatus"));
        assertFalse(setters.contains("setEmailVerified"));
        assertFalse(setters.contains("setPassword"));
        assertFalse(setters.contains("setOtpHash"));
    }

    @Test
    void changePasswordRequest_exposesSettersForTheTwoSecretsOnly() {
        Set<String> setters = Arrays.stream(com.giftconnect.dto.ChangePasswordRequest.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("set"))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("setCurrentPassword", "setNewPassword"), setters);
        assertFalse(setters.contains("setUserId"));
        assertFalse(setters.contains("setRole"));
    }
}
