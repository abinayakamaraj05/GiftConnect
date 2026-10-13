package com.giftconnect.controller;

import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.EmailService;
import com.giftconnect.service.OtpService;
import com.giftconnect.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4O regression tests for POST /api/auth/register.
 *
 * A NEW account must generate + store an OTP and actually email it (the
 * pre-fix code returned a "code was sent" message without ever sending one).
 * The real AuthFilter allows this path for anonymous callers; only the service
 * layer is mocked, so no database or SMTP is touched and no email is ever sent.
 */
@WebMvcTest(AuthController.class)
class AuthControllerRegisterTest {

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

    private static final String VALID_BODY =
            "{\"name\":\"Riya\",\"email\":\"riya@example.com\",\"password\":\"StrongPass1!\","
                    + "\"phone\":\"9999999999\",\"address\":\"Mumbai\"}";

    private User newUnverifiedUser() {
        User user = new User();
        user.setUserId(201L);
        user.setName("Riya");
        user.setEmail("riya@example.com");
        user.setRole(Role.CUSTOMER);
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void register_newAccount_generatesStoresAndEmailsOtp() throws Exception {
        User expected = newUnverifiedUser();
        when(userService.findByEmail("riya@example.com")).thenReturn(Optional.empty());
        when(userService.registerWithOtp(any())).thenReturn(expected);
        when(otpService.generateAndStoreOtp(any())).thenReturn("204810");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("riya@example.com"));

        // The OTP is generated for the newly created (still unverified) user…
        verify(otpService).generateAndStoreOtp(expected);
        // …and the SAME raw OTP is emailed to the correct recipient/name…
        verify(emailService).sendOtpEmail("riya@example.com", "204810", "Riya");
        // …and the OTP fields are persisted so verify-otp can accept it later.
        verify(userService).save(expected);
        verify(activityLogService).log(eq(ActivityType.AUTH_REGISTER), eq(ActivityStatus.SUCCESS),
                eq(201L), eq(Role.CUSTOMER), eq("riya@example.com"), eq("USER"), eq(201L),
                anyString(), any(HttpServletRequest.class));
    }

    @Test
    void register_unverifiedReRegistration_stillSendsOtp() throws Exception {
        User existing = newUnverifiedUser();
        existing.setEmail("pending@example.com");
        existing.setName("Old");
        when(userService.findByEmail("pending@example.com")).thenReturn(Optional.of(existing));
        when(otpService.generateAndStoreOtp(existing)).thenReturn("981234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Old\",\"email\":\"pending@example.com\","
                                + "\"password\":\"StrongPass1!\",\"phone\":\"111\",\"address\":\"a\"}"))
                .andExpect(status().isAccepted());

        verify(otpService).assertResendAllowed(existing);
        verify(otpService).generateAndStoreOtp(existing);
        verify(emailService).sendOtpEmail("pending@example.com", "981234", "Old");
        verify(userService).save(existing);
    }

    @Test
    void register_verifiedAccount_sendsNoEmail() throws Exception {
        User verified = newUnverifiedUser();
        verified.setEmailVerified(true);
        when(userService.findByEmail("riya@example.com")).thenReturn(Optional.of(verified));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted());

        verify(emailService, never()).sendOtpEmail(any(), any(), any());
        verify(otpService, never()).generateAndStoreOtp(any());
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_returnsAcceptedWithoutSending() throws Exception {
        when(userService.findByEmail("riya@example.com")).thenReturn(Optional.empty());
        when(userService.registerWithOtp(any()))
                .thenThrow(new IllegalStateException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted());

        verify(emailService, never()).sendOtpEmail(any(), any(), any());
        verify(otpService, never()).generateAndStoreOtp(any());
        verify(userService, never()).save(any(User.class));
    }

    @Test
    void register_weakPassword_isRejectedBeforeAnyWork() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Riya\",\"email\":\"riya@example.com\","
                                + "\"password\":\"short\",\"phone\":\"111\",\"address\":\"a\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).findByEmail(any());
        verify(userService, never()).registerWithOtp(any());
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    void register_newAccount_whenMailSendFails_returns500AndDoesNotPersistOtp() throws Exception {
        User expected = newUnverifiedUser();
        when(userService.findByEmail("riya@example.com")).thenReturn(Optional.empty());
        when(userService.registerWithOtp(any())).thenReturn(expected);
        when(otpService.generateAndStoreOtp(any())).thenReturn("204810");
        doThrow(new MailSendException("smtp unavailable")).when(emailService)
                .sendOtpEmail(any(), any(), any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError());

        // OTP fields are only persisted after a successful send — same ordering
        // as the re-registration branch, so a failed send leaves no dangling OTP.
        verify(userService, never()).save(any(User.class));
    }
}