package com.giftconnect.controller;

import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.repository.OrderIdempotencyRepository;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.DeliveryPolicy;
import com.giftconnect.service.OrderService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4B — fulfillment status advancement is ADMIN-only, proven through the
 * REAL OrderService and the real GlobalExceptionHandler rather than a mocked
 * service hand-configured to return 403.
 *
 * @WebMvcTest loads the real AuthFilter and @RestControllerAdvice; @Import adds
 * the real OrderService backed by mocked repositories. An owning CUSTOMER trying
 * to advance their own order therefore flows through
 * AccessControl.requireAdmin -> AccessDeniedException -> 403 exactly as in
 * production. No database, SMTP, or production data is involved.
 */
@WebMvcTest(OrderController.class)
@Import(OrderService.class)
class OrderControllerFulfillmentAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;
    @MockitoBean
    private ProductRepository productRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private ActivityLogService activityLogService;
    @MockitoBean
    private OrderIdempotencyRepository orderIdempotencyRepository;

    // The real OrderService is @Import-ed here, so its new scheduled-delivery
    // dependency must be present. Mocked (never invoked by these fulfillment
    // tests); the seven-day rule is covered in DeliveryPolicyTest.
    @MockitoBean
    private DeliveryPolicy deliveryPolicy;

    @MockitoBean
    private UserService userService;

    private static final String CONFIRM_BODY = "{\"status\":\"CONFIRMED\"}";
    private static final String DELIVERED_BODY = "{\"status\":\"DELIVERED\"}";
    private static final Long CUSTOMER_ID = 1L;
    private static final Long ADMIN_ID = 9L;

    private MockHttpSession session(Long userId, Role role) {
        TestSessions.stubActiveSession(userService, userId, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, userId);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    private Order orderOwnedBy(Long orderId, Long ownerId, OrderStatus status) {
        User owner = new User();
        owner.setUserId(ownerId);
        owner.setRole(Role.CUSTOMER);
        owner.setEmail("owner" + ownerId + "@example.com");

        Order order = new Order();
        order.setOrderId(orderId);
        order.setUser(owner);
        order.setStatus(status);
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    @Test
    void updateStatus_withoutSession_is401() throws Exception {
        mockMvc.perform(put("/api/orders/5/status")
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatus_asOwningCustomer_is403() throws Exception {
        when(orderRepository.findById(5L))
                .thenReturn(Optional.of(orderOwnedBy(5L, CUSTOMER_ID, OrderStatus.PENDING)));

        mockMvc.perform(put("/api/orders/5/status").session(session(CUSTOMER_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_asOwningCustomerMarkingDelivered_is403() throws Exception {
        when(orderRepository.findById(5L))
                .thenReturn(Optional.of(orderOwnedBy(5L, CUSTOMER_ID, OrderStatus.SHIPPED)));

        mockMvc.perform(put("/api/orders/5/status").session(session(CUSTOMER_ID, Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(DELIVERED_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_asAdminForwardTransition_is200() throws Exception {
        // Positive control: the real service is wired, so the 403s above cannot be
        // a false pass from a misconfigured context.
        Order existing = orderOwnedBy(5L, CUSTOMER_ID, OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/orders/5/status").session(session(ADMIN_ID, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY))
                .andExpect(status().isOk());
    }
}
