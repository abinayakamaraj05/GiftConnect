package com.giftconnect.controller;

import com.giftconnect.dto.ProfileUpdateRequest;
import com.giftconnect.dto.UserResponse;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioural tests for the self-service profile endpoints:
 *
 *   GET /api/users/me -> own profile, safe DTO
 *   PUT /api/users/me -> update own name/phone/address + PROFILE_UPDATED audit
 *
 * The real AuthFilter runs, so "no session" and "session exists" are tested
 * end-to-end; only UserService and ActivityLogService are mocked.
 */
@WebMvcTest(UserController.class)
class UserControllerProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ActivityLogService activityLogService;

    private static final Long ACTOR_ID = 11L;
    private static final Long SOMEONE_ELSE = 99L;

    private MockHttpSession session() {
        TestSessions.stubActiveSession(userService, ACTOR_ID, Role.CUSTOMER);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, Role.CUSTOMER);
        return session;
    }

    /** A session whose authoritative database row is INACTIVE (filter must stop it). */
    private MockHttpSession deactivatedSession() {
        when(userService.getUserByIdOptional(ACTOR_ID))
                .thenReturn(Optional.of(TestSessions.user(ACTOR_ID, Role.CUSTOMER, AccountStatus.INACTIVE)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, Role.CUSTOMER);
        return session;
    }

    /** A row carrying every field the response DTO must NOT expose. */
    private User sensitiveUser(Long id) {
        User user = TestSessions.activeUser(id, Role.CUSTOMER);
        user.setPassword("$2a$10$notARealBcryptHashValueForTests0000000000000000000000000");
        user.setOtpHash("$2a$10$notARealOtpHashValueForTests00000000000000000000000000");
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        user.setOtpAttempts(3);
        user.setOtpResendTime(LocalDateTime.now());
        user.setPhone("9999999999");
        user.setAddress("Mumbai");
        return user;
    }

    private void verifyNoProfileAudit() {
        verify(activityLogService, never()).log(
                eq(ActivityType.PROFILE_UPDATED), any(ActivityStatus.class),
                any(HttpServletRequest.class), any(), any(), any());
    }

    // ==================================================================
    // GET /api/users/me
    // ==================================================================

    @Test
    void getMe_authenticated_returnsOwnProfileWithoutCredentialFields() throws Exception {
        session();
        when(userService.getUserById(ACTOR_ID)).thenReturn(sensitiveUser(ACTOR_ID));

        mockMvc.perform(get("/api/users/me").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ACTOR_ID))
                .andExpect(jsonPath("$.name").value("Test Actor"))
                .andExpect(jsonPath("$.email").value("actor@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.otpHash").doesNotExist())
                .andExpect(jsonPath("$.otpExpiry").doesNotExist())
                .andExpect(jsonPath("$.otpAttempts").doesNotExist())
                .andExpect(jsonPath("$.otpResendTime").doesNotExist());

        verify(userService).getUserById(ACTOR_ID);
        // Reading your own profile is not an audited event.
        verifyNoProfileAudit();
    }

    @Test
    void getMe_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void getMe_readsIdentityFromTheSessionEvenWhenAnIdIsOnTheUrl() throws Exception {
        MockHttpSession session = session();
        when(userService.getUserById(ACTOR_ID)).thenReturn(sensitiveUser(ACTOR_ID));

        // ?userId=99 is not a parameter this endpoint reads at all.
        mockMvc.perform(get("/api/users/me?userId=" + SOMEONE_ELSE).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ACTOR_ID));

        verify(userService).getUserById(ACTOR_ID);
        verify(userService, never()).getUserById(SOMEONE_ELSE);
    }

    // ==================================================================
    // PUT /api/users/me
    // ==================================================================

    @Test
    void putMe_authenticated_updatesOwnProfileAndWritesAuditEvent() throws Exception {
        MockHttpSession session = session();
        when(userService.updateProfile(eq(ACTOR_ID), any()))
                .thenAnswer(inv -> UserResponse.fromEntity(sensitiveUser(ACTOR_ID)));

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya Sharma\",\"phone\":\"9876543210\",\"address\":\"Chennai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ACTOR_ID))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.otpHash").doesNotExist());

        ArgumentCaptor<ProfileUpdateRequest> captor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(userService).updateProfile(eq(ACTOR_ID), captor.capture());
        assertEquals("Riya Sharma", captor.getValue().getName());
        assertEquals("9876543210", captor.getValue().getPhone());
        assertEquals("Chennai", captor.getValue().getAddress());

        verify(activityLogService).log(
                eq(ActivityType.PROFILE_UPDATED), eq(ActivityStatus.SUCCESS),
                any(HttpServletRequest.class), eq("USER"), eq(ACTOR_ID), anyString());
    }

    @Test
    void putMe_withoutSession_is401_andNeverTouchesTheService() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
        verifyNoProfileAudit();
    }

    @Test
    void putMe_blankName_is400_withoutServiceCallOrAudit() throws Exception {
        MockHttpSession session = session();

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"phone\":\"999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(userService, never()).updateProfile(any(), any());
        verifyNoProfileAudit();
    }

    @Test
    void putMe_nameLongerThanTheEntityAllows_is400() throws Exception {
        MockHttpSession session = session();
        String tooLong = "x".repeat(101);

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());

        verify(userService, never()).updateProfile(any(), any());
        verifyNoProfileAudit();
    }

    @Test
    void putMe_ignoresAUserIdInTheQueryString_andTargetsTheSessionUser() throws Exception {
        MockHttpSession session = session();
        when(userService.updateProfile(eq(ACTOR_ID), any()))
                .thenAnswer(inv -> UserResponse.fromEntity(sensitiveUser(ACTOR_ID)));

        mockMvc.perform(put("/api/users/me?userId=" + SOMEONE_ELSE)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya\"}"))
                .andExpect(status().isOk());

        verify(userService).updateProfile(eq(ACTOR_ID), any());
        verify(userService, never()).updateProfile(eq(SOMEONE_ELSE), any());
    }

    @Test
    void putAnotherUser_isNotMapped_soNoAccountCanBeTargetedByPath() throws Exception {
        MockHttpSession session = session();

        // There is deliberately no PUT /api/users/{id} — only /me exists, so the
        // request cannot even name another account. (The project's generic
        // exception handler reports the unmapped method as a server-side error
        // rather than the textbook 405; either way nothing is updated.)
        mockMvc.perform(put("/api/users/" + SOMEONE_ELSE)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() >= 400,
                        "an update aimed at another user's path must be rejected, got "
                                + result.getResponse().getStatus()));

        verify(userService, never()).updateProfile(any(), any());
        verifyNoProfileAudit();
    }

    @Test
    void putMe_bodyWithPrivilegedFields_cannotReachThemBecauseTheDtoHasNoSetters() throws Exception {
        MockHttpSession session = session();
        when(userService.updateProfile(eq(ACTOR_ID), any()))
                .thenAnswer(inv -> UserResponse.fromEntity(sensitiveUser(ACTOR_ID)));

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya\","
                                + "\"email\":\"attacker@example.com\","
                                + "\"role\":\"ADMIN\","
                                + "\"accountStatus\":\"ACTIVE\","
                                + "\"emailVerified\":false,"
                                + "\"password\":\"Hijacked1!\","
                                + "\"otpHash\":\"leaked\","
                                + "\"userId\":" + SOMEONE_ELSE + "}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfileUpdateRequest> captor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(userService).updateProfile(eq(ACTOR_ID), captor.capture());
        // Only the editable field bound; the privileged ones have nowhere to go.
        assertEquals("Riya", captor.getValue().getName());
        assertEquals(null, captor.getValue().getPhone());
        assertEquals(null, captor.getValue().getAddress());
    }

    @Test
    void putMe_whenPersistenceFails_is500_withoutSuccessAudit() throws Exception {
        MockHttpSession session = session();
        when(userService.updateProfile(eq(ACTOR_ID), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya\"}"))
                .andExpect(status().isInternalServerError());

        verifyNoProfileAudit();
    }

    // ==================================================================
    // Deactivated accounts must not reach either endpoint
    // ==================================================================

    @Test
    void getMe_inactiveAccount_is403_andSessionIsDestroyed() throws Exception {
        MockHttpSession session = deactivatedSession();

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(userService, never()).getUserById(any());
        verifyNoProfileAudit();
        assertTrue(session.isInvalid(), "a deactivated account's session must be destroyed");
    }

    @Test
    void putMe_inactiveAccount_is403_andNeverUpdatesProfileOrAudits() throws Exception {
        MockHttpSession session = deactivatedSession();

        mockMvc.perform(put("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        verify(userService, never()).updateProfile(any(), any());
        verifyNoProfileAudit();
        assertTrue(session.isInvalid(), "a deactivated account's session must be destroyed");
    }
}
