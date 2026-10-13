package com.giftconnect.controller;

import com.giftconnect.entity.Role;
import com.giftconnect.service.OrderService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules for /api/orders at the HTTP layer.
 *
 * Platform-wide listing is ADMIN-only; per-user listing is self-or-ADMIN.
 * Ownership checks that need the order itself (get-by-id, status, cancel) are
 * covered in OrderServiceAuthTest. The service is mocked here so no database is
 * involved; these tests still exercise the real filter + controller guards.
 */
@WebMvcTest(OrderController.class)
class OrderControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserService userService;

    private static final Long ACTOR_ID = 1L;
    private static final String BODY =
            "{\"shippingAddress\":\"addr\",\"items\":[{\"productId\":1,\"quantity\":1}]}";

    private MockHttpSession session(Long userId, Role role) {
        TestSessions.stubActiveSession(userService, userId, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, userId);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    @Test
    void listAll_withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void listAll_asCustomer_is403() throws Exception {
        mockMvc.perform(get("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_asAdmin_is200() throws Exception {
        mockMvc.perform(get("/api/orders").session(session(ACTOR_ID, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void listByUser_ownId_asCustomer_is200() throws Exception {
        mockMvc.perform(get("/api/orders/user/" + ACTOR_ID).session(session(ACTOR_ID, Role.CUSTOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void listByUser_otherId_asCustomer_is403() throws Exception {
        mockMvc.perform(get("/api/orders/user/999").session(session(ACTOR_ID, Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listByUser_otherId_asAdmin_is200() throws Exception {
        mockMvc.perform(get("/api/orders/user/999").session(session(ACTOR_ID, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void create_withoutSession_is401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asCustomer_reachesServiceAndIs201() throws Exception {
        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        // The owner is bound server-side: the controller delegates to the service
        // with the request (session), not with any client-supplied user id.
        verify(orderService).createOrder(any(), any(), any());
    }

    @Test
    void create_withIdempotencyKey_forwardsValueToService() throws Exception {
        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .header("Idempotency-Key", "checkout-123")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        // Phase 4I — the optional header is forwarded verbatim to the service.
        verify(orderService).createOrder(any(), eq("checkout-123"), any());
    }

    // ------------------------------------------------------------------
    // Phase 4E — shippingAddress validation (@NotBlank + @Size(max=500))
    // ------------------------------------------------------------------

    private static String bodyWithAddress(String address) {
        return "{\"shippingAddress\":\"" + address + "\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
    }

    @Test
    void create_withMissingAddress_is400() throws Exception {
        String body = "{\"items\":[{\"productId\":1,\"quantity\":1}]}";

        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withWhitespaceOnlyAddress_is400() throws Exception {
        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(bodyWithAddress("   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_with500CharAddress_is201() throws Exception {
        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(bodyWithAddress("a".repeat(500))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_with501CharAddress_is400() throws Exception {
        mockMvc.perform(post("/api/orders").session(session(ACTOR_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(bodyWithAddress("a".repeat(501))))
                .andExpect(status().isBadRequest());
    }
}
