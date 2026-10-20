package com.giftconnect.controller;

import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.EmailService;
import com.giftconnect.service.OtpService;
import com.giftconnect.service.RateLimitService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the login/registration hardening: session-id rotation on login (R4),
 * rate limiting on the credential endpoints (R1), and a registration response
 * that is identical for new and existing emails (R2).
 *
 * The real AuthFilter and CsrfProtectionFilter run in the slice (no Origin header
 * is sent, which the CSRF filter allows); services are mocked so no database,
 * SMTP or payment is touched.
 */
@WebMvcTest(AuthController.class)
class AuthControllerLoginTest {

    private static final String NEUTRAL_REGISTER_MESSAGE =
            "If this email is not already verified, a verification code has been sent.";

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

    @MockitoBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void allowRequestsThroughTheRateLimiter() {
        when(rateLimitService.tryConsume(anyString())).thenReturn(true);
    }

    private static final String LOGIN_BODY =
            "{\"email\":\"user@example.com\",\"password\":\"Secret1!\"}";

    private String registerBody(String email) {
        return "{\"name\":\"Riya\",\"email\":\"" + email + "\",\"password\":\"StrongPass1!\","
                + "\"phone\":\"9999999999\",\"address\":\"Mumbai\"}";
    }

    // ------------------------------------------------------------------
    // R4 — session fixation: rotate the session id on successful login
    // ------------------------------------------------------------------

    @Test
    void successfulLogin_rotatesTheSessionId() throws Exception {
        when(userService.authenticate("user@example.com", "Secret1!"))
                .thenReturn(Optional.of(TestSessions.activeUser(9L, Role.CUSTOMER)));

        MockHttpSession session = new MockHttpSession();
        String preLoginId = session.getId();

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk());

        assertNotEquals(preLoginId, session.getId(),
                "the session id must change on login to defeat session fixation");
        assertEquals(9L, session.getAttribute(AuthController.SESSION_USER_ID));
    }

    // ------------------------------------------------------------------
    // R1 — rate limiting on login and register
    // ------------------------------------------------------------------

    @Test
    void login_rateLimited_is429_andNeverAuthenticates() throws Exception {
        when(rateLimitService.tryConsume(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists());

        verify(userService, never()).authenticate(any(), any());
    }

    @Test
    void register_rateLimited_is429_andNeverCreatesAnAccount() throws Exception {
        when(rateLimitService.tryConsume(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("new@example.com")))
                .andExpect(status().isTooManyRequests());

        verify(userService, never()).findByEmail(any());
        verify(userService, never()).registerWithOtp(any());
    }

    @Test
    void verifyOtp_rateLimited_is429_andNeverLooksUpTheUser() throws Exception {
        when(rateLimitService.tryConsume(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isTooManyRequests());

        verify(userService, never()).findByEmail(any());
    }

    @Test
    void resendOtp_rateLimited_is429_andNeverLooksUpTheUser() throws Exception {
        when(rateLimitService.tryConsume(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isTooManyRequests());

        verify(userService, never()).findByEmail(any());
    }

    // ------------------------------------------------------------------
    // R2 — registration response is identical for new and existing emails
    // ------------------------------------------------------------------

    @Test
    void registrationResponse_isIdenticalForNewAndExistingEmails() throws Exception {
        // Brand-new email.
        User created = new User();
        created.setUserId(11L);
        created.setName("Riya");
        created.setEmail("new@example.com");
        created.setRole(Role.CUSTOMER);
        created.setEmailVerified(false);
        when(userService.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userService.registerWithOtp(any())).thenReturn(created);
        when(otpService.generateAndStoreOtp(any())).thenReturn("123456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("new@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(NEUTRAL_REGISTER_MESSAGE));

        // Already-registered, verified email.
        User verified = new User();
        verified.setUserId(12L);
        verified.setEmail("old@example.com");
        verified.setRole(Role.CUSTOMER);
        verified.setEmailVerified(true);
        when(userService.findByEmail("old@example.com")).thenReturn(Optional.of(verified));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("old@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(NEUTRAL_REGISTER_MESSAGE));
    }
}
