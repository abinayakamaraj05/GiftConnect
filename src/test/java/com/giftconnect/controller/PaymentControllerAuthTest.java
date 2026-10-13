package com.giftconnect.controller;

import com.giftconnect.entity.Role;
import com.giftconnect.service.PaymentService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer checks for /api/payments. Payment ownership (order owner-or-ADMIN)
 * is enforced inside PaymentService and covered by PaymentServiceAuthTest; here
 * we confirm the filter requires a session and that authenticated calls reach
 * the service. The service is mocked, so no database or SMTP is involved.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private UserService userService;

    private static final String BODY = "{\"orderId\":1,\"paymentMethod\":\"UPI\"}";

    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, 1L, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, 1L);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    @Test
    void create_withoutSession_is401() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asCustomer_reachesServiceAndIs201() throws Exception {
        mockMvc.perform(post("/api/payments").session(session(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void get_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/payments/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_asCustomer_reachesServiceAndIs200() throws Exception {
        mockMvc.perform(get("/api/payments/1").session(session(Role.CUSTOMER)))
                .andExpect(status().isOk());
    }
}
