package com.giftconnect.controller;

import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security regression tests for /api/users.
 *
 * Phase 5 fix: both GET /api/users and GET /api/users/{id} used to serialize the
 * raw User entity, which carries the BCrypt password hash and the OTP hash plus its
 * expiry/attempt/cooldown fields. They now return the UserResponse DTO. These tests
 * feed the service a user that deliberately carries every sensitive field and assert
 * none of them appear in the JSON body.
 */
@WebMvcTest(UserController.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final Long ACTOR_ID = 7L;
    private static final Long TARGET_ID = 3L;

    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, ACTOR_ID, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    /** A user row carrying every field the DTO must NOT expose. */
    private User sensitiveUser(Long id, Role role) {
        User user = TestSessions.activeUser(id, role);
        user.setPassword("$2a$10$notARealBcryptHashValueForTests000000000000000000000000");
        user.setOtpHash("$2a$10$notARealOtpHashValueForTests00000000000000000000000000");
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        user.setOtpAttempts(2);
        user.setOtpResendTime(LocalDateTime.now());
        return user;
    }

    @Test
    void list_asAdmin_returns200_withoutCredentialFields() throws Exception {
        when(userService.getAllUsers())
                .thenReturn(List.of(sensitiveUser(TARGET_ID, Role.CUSTOMER)));

        mockMvc.perform(get("/api/users").session(session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("actor@example.com"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].otpHash").doesNotExist())
                .andExpect(jsonPath("$[0].otpExpiry").doesNotExist())
                .andExpect(jsonPath("$[0].otpAttempts").doesNotExist())
                .andExpect(jsonPath("$[0].otpResendTime").doesNotExist());
    }

    @Test
    void list_asNonAdmin_is403() throws Exception {
        mockMvc.perform(get("/api/users").session(session(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_asAdmin_returns200_withoutCredentialFields() throws Exception {
        when(userService.getUserById(TARGET_ID))
                .thenReturn(sensitiveUser(TARGET_ID, Role.CUSTOMER));

        mockMvc.perform(get("/api/users/{id}", TARGET_ID).session(session(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.email").value("actor@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.otpHash").doesNotExist())
                .andExpect(jsonPath("$.otpExpiry").doesNotExist())
                .andExpect(jsonPath("$.otpAttempts").doesNotExist())
                .andExpect(jsonPath("$.otpResendTime").doesNotExist());
    }

    @Test
    void getById_asCustomerViewingOwnRecord_returns200_withoutCredentialFields() throws Exception {
        when(userService.getUserById(ACTOR_ID))
                .thenReturn(sensitiveUser(ACTOR_ID, Role.CUSTOMER));

        mockMvc.perform(get("/api/users/{id}", ACTOR_ID).session(session(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.otpHash").doesNotExist());
    }

    @Test
    void getById_asCustomerViewingOtherRecord_is403() throws Exception {
        mockMvc.perform(get("/api/users/{id}", TARGET_ID).session(session(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", TARGET_ID))
                .andExpect(status().isUnauthorized());
    }
}
