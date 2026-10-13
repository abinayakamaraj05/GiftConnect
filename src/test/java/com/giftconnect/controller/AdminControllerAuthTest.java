package com.giftconnect.controller;

import com.giftconnect.dto.SystemOverviewResponse;
import com.giftconnect.entity.Role;
import com.giftconnect.service.AdminService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 — the ADMIN API must stay fully protected. Anonymous callers are
 * rejected by AuthFilter with 401; authenticated non-admins get 403 from
 * AdminController; only ADMIN reaches the service.
 */
@WebMvcTest(AdminController.class)
class AdminControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private UserService userService;

    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, 7L, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, 7L);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    @Test
    void overview_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_asCustomer_is403() throws Exception {
        mockMvc.perform(get("/api/admin/overview").session(session(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void overview_asAdmin_is200() throws Exception {
        when(adminService.getOverview()).thenReturn(new SystemOverviewResponse());

        mockMvc.perform(get("/api/admin/overview").session(session(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void users_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
