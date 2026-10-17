package com.giftconnect.controller;

import com.giftconnect.dto.ChangePasswordRequest;
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
import com.giftconnect.util.TestSessions;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioural tests for POST /api/auth/change-password.
 *
 * The real AuthFilter runs, so authentication and the INACTIVE-account check
 * are exercised end-to-end; only the services are mocked, so no database, no
 * SMTP and no real password write is touched.
 *
 * The password-correctness / BCrypt behaviour itself lives in
 * {@code UserServiceAccountTest}, because UserService owns it — here we only
 * decide WHERE a given outcome lands (status code, audit event or none).
 */
@WebMvcTest(AuthController.class)
class AuthControllerChangePasswordTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ActivityLogService activityLogService;

    private static final Long ACTOR_ID = 42L;
    private static final String CURRENT = "OldPass1!";
    private static final String NEW = "NewPass1!";
    private static final String VALID_BODY =
            "{\"currentPassword\":\"" + CURRENT + "\",\"newPassword\":\"" + NEW + "\"}";

    /** Active-session helper: also stubs the authoritative row the filter reads. */
    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, ACTOR_ID, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    /** A session whose account row is INACTIVE (AuthFilter must destroy it). */
    private MockHttpSession deactivatedSession() {
        when(userService.getUserByIdOptional(ACTOR_ID))
                .thenReturn(Optional.of(TestSessions.user(ACTOR_ID, Role.CUSTOMER, AccountStatus.INACTIVE)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, Role.CUSTOMER);
        return session;
    }

    /** No AUTH_PASSWORD_CHANGED row may exist unless the change really succeeded. */
    private void verifyNoSuccessAudit() {
        verify(activityLogService, never()).log(
                eq(ActivityType.AUTH_PASSWORD_CHANGED), any(ActivityStatus.class),
                any(), any(), any(), any(), any(), any(), any(HttpServletRequest.class));
    }

    // ------------------------------------------------------------------
    // Authentication / account status (enforced by AuthFilter before the controller)
    // ------------------------------------------------------------------

    @Test
    void changePassword_withoutSession_is401_andDoesNothing() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_inactiveAccount_is403_andNeverRotatesCredentials() throws Exception {
        MockHttpSession session = deactivatedSession();

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(userService, never()).changePassword(any(), any(), any());
        verifyNoSuccessAudit();
        // AuthFilter invalidates the session; MockHttpSession then refuses all access.
        assertThrows(IllegalStateException.class,
                () -> session.getAttribute(AuthController.SESSION_USER_ID));
    }

    // ------------------------------------------------------------------
    // Success path
    // ------------------------------------------------------------------

    @Test
    void changePassword_success_usesSessionIdentity_andWritesSuccessAudit() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doNothing().when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully."));

        // Identity came from the session — never from the body or the URL.
        verify(userService).changePassword(ACTOR_ID, CURRENT, NEW);
        verify(activityLogService).log(
                eq(ActivityType.AUTH_PASSWORD_CHANGED), eq(ActivityStatus.SUCCESS),
                eq(ACTOR_ID), eq(Role.CUSTOMER), eq("actor@example.com"),
                eq("USER"), eq(ACTOR_ID), anyString(), any(HttpServletRequest.class));

        // The caller stays signed in (no silent session invalidation).
        assertNotNull(session.getAttribute(AuthController.SESSION_USER_ID),
                "a successful password change must not sign the caller out");
    }

    @Test
    void changePassword_ignoresAnyUserIdTheClientSends() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doNothing().when(userService).changePassword(any(), any(), any());

        // A user id on the URL is simply not a parameter this endpoint reads.
        mockMvc.perform(post("/api/auth/change-password?userId=9999")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        verify(userService).changePassword(ACTOR_ID, CURRENT, NEW);
        verify(userService, never()).changePassword(eq(9999L), any(), any());
    }

    // ------------------------------------------------------------------
    // Rejections — every one must be 400 AND must not write a success event
    // ------------------------------------------------------------------

    @Test
    void changePassword_wrongCurrentPassword_is400_withoutSuccessAudit() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doThrow(new InvalidOperationException("Current password is incorrect."))
                .when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect."));

        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_weakNewPassword_is400_withoutSuccessAudit() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doThrow(new InvalidOperationException(
                        "Password must be at least 8 characters long; Password must contain at least 1 number"))
                .when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("at least 8 characters")));

        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_reusedPassword_is400_withoutSuccessAudit() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doThrow(new InvalidOperationException(
                        "New password must be different from your current password."))
                .when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("different from your current")));

        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_missingFields_is400_andNeverReachesTheService() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.currentPassword").exists())
                .andExpect(jsonPath("$.newPassword").exists());

        verify(userService, never()).changePassword(any(), any(), any());
        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_blankFields_is400_andNeverReachesTheService() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"   \",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
        verifyNoSuccessAudit();
    }

    @Test
    void changePassword_whenPersistenceFails_is500_withoutSuccessAudit() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doThrow(new RuntimeException("database unavailable"))
                .when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError());

        verifyNoSuccessAudit();
    }

    // ------------------------------------------------------------------
    // Response must never echo credential material
    // ------------------------------------------------------------------

    @Test
    void changePassword_responseNeverEchoesPasswords() throws Exception {
        MockHttpSession session = session(Role.CUSTOMER);
        doNothing().when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.newPassword").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString(CURRENT))))
                .andExpect(content().string(not(containsString(NEW))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    // ------------------------------------------------------------------
    // The DTO itself cannot carry an identity or a privileged field
    // ------------------------------------------------------------------

    @Test
    void changePasswordRequest_hasNoFieldThatCouldSelectAnotherAccount() {
        Set<String> setters = Arrays.stream(ChangePasswordRequest.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("set"))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("setCurrentPassword", "setNewPassword"), setters);
    }
}
