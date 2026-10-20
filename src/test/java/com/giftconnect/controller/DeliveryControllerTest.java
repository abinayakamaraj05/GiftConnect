package com.giftconnect.controller;

import com.giftconnect.entity.Role;
import com.giftconnect.service.DeliveryPolicy;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for GET /api/delivery/options.
 *
 * The real AuthFilter is in the slice (like ProductControllerAuthTest), so the
 * endpoint is verified to require a session — it is deliberately NOT on the
 * public allowlist because scheduling happens during an authenticated checkout.
 * DeliveryPolicy is mocked: the seven-day rule itself is covered deterministically
 * in DeliveryPolicyTest, and its integration with checkout in
 * OrderServiceSchedulingTest.
 */
@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryPolicy deliveryPolicy;

    @MockitoBean
    private UserService userService;

    private static final Long ACTOR_ID = 7L;

    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, ACTOR_ID, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    @Test
    void options_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/delivery/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_withSession_returnsPolicyMetadataAndSlots() throws Exception {
        when(deliveryPolicy.today()).thenReturn(LocalDate.parse("2026-10-01"));
        when(deliveryPolicy.earliestEligibleDate()).thenReturn(LocalDate.parse("2026-10-08"));
        when(deliveryPolicy.timeZoneId()).thenReturn("Asia/Kolkata");

        mockMvc.perform(get("/api/delivery/options").session(session(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-01"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.minAdvanceCalendarDays").value(7))
                .andExpect(jsonPath("$.earliestEligibleDate").value("2026-10-08"))
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].code").value("09:00-12:00"))
                .andExpect(jsonPath("$.slots[0].label").value("9:00 AM - 12:00 PM"))
                .andExpect(jsonPath("$.slots[1].code").value("12:00-15:00"))
                .andExpect(jsonPath("$.slots[2].code").value("15:00-18:00"));
    }
}
