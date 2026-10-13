package com.giftconnect.service;

import com.giftconnect.controller.AuthController;
import com.giftconnect.dto.PaymentRequest;
import com.giftconnect.dto.PaymentResponse;
import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;
import com.giftconnect.entity.Payment;
import com.giftconnect.entity.PaymentStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.AccessDeniedException;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization + state tests for PaymentService. Every payment action must be
 * scoped to the owning order's customer (or ADMIN), and a payment may only be
 * processed once, from PENDING. Repositories are mocked — no DB, no SMTP.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceAuthTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private PaymentService paymentService;

    private HttpServletRequest sessionRequest(Long userId, Role role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_ID)).thenReturn(userId);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_ROLE)).thenReturn(role);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_EMAIL)).thenReturn("actor@example.com");
        return request;
    }

    private Order order(Long id, User owner) {
        Order order = new Order();
        order.setOrderId(id);
        order.setUser(owner);
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    private Order order(Long id, User owner, OrderStatus status) {
        Order order = order(id, owner);
        order.setStatus(status);
        return order;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setUserId(id);
        user.setRole(role);
        user.setEmail("user" + id + "@example.com");
        return user;
    }

    private PaymentRequest paymentRequest(Long orderId) {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setPaymentMethod("UPI");
        return request;
    }

    @Test
    void createPayment_deniesOtherCustomersOrder() {
        Order other = order(9L, user(2L, Role.CUSTOMER));
        when(orderService.findOrderOrThrow(9L)).thenReturn(other);

        assertThrows(AccessDeniedException.class,
                () -> paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void createPayment_allowsOwner() {
        Order own = order(9L, user(1L, Role.CUSTOMER));
        when(orderService.findOrderOrThrow(9L)).thenReturn(own);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.CUSTOMER));

        assertEquals(9L, response.getOrderId());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
    }

    @Test
    void createPayment_allowsAdminOnAnothersOrder() {
        Order other = order(9L, user(2L, Role.CUSTOMER));
        when(orderService.findOrderOrThrow(9L)).thenReturn(other);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.ADMIN));

        assertEquals(9L, response.getOrderId());
    }

    @Test
    void getPaymentById_deniesNonOwner() {
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(order(9L, user(2L, Role.CUSTOMER)));
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        assertThrows(AccessDeniedException.class,
                () -> paymentService.getPaymentById(3L, sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void processPayment_deniesNonOwner() {
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(order(9L, user(2L, Role.CUSTOMER)));
        payment.setPaymentStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        assertThrows(AccessDeniedException.class,
                () -> paymentService.processPayment(3L, false, sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void processPayment_rejectsAlreadyProcessedPayment() {
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(order(9L, user(1L, Role.CUSTOMER)));
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        assertThrows(InvalidOperationException.class,
                () -> paymentService.processPayment(3L, false, sessionRequest(1L, Role.CUSTOMER)));
    }

    // ------------------------------------------------------------------
    // Phase 4D — no payments for CANCELLED / DELIVERED orders
    // ------------------------------------------------------------------

    @Test
    void createPayment_rejectsCancelledOrder() {
        Order cancelled = order(9L, user(1L, Role.CUSTOMER), OrderStatus.CANCELLED);
        when(orderService.findOrderOrThrow(9L)).thenReturn(cancelled);

        assertThrows(InvalidOperationException.class,
                () -> paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.CUSTOMER)));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_rejectsDeliveredOrder() {
        Order delivered = order(9L, user(1L, Role.CUSTOMER), OrderStatus.DELIVERED);
        when(orderService.findOrderOrThrow(9L)).thenReturn(delivered);

        assertThrows(InvalidOperationException.class,
                () -> paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.CUSTOMER)));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processPayment_rejectsWhenOrderCancelledSinceCreation() {
        Order cancelled = order(9L, user(1L, Role.CUSTOMER), OrderStatus.CANCELLED);
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(cancelled);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        assertThrows(InvalidOperationException.class,
                () -> paymentService.processPayment(3L, false, sessionRequest(1L, Role.CUSTOMER)));

        // Rejection writes nothing and never marks the order paid.
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderService, never()).markOrderAsPaid(any(Order.class));
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
    }

    @Test
    void processPayment_rejectsWhenOrderDeliveredSinceCreation() {
        Order delivered = order(9L, user(1L, Role.CUSTOMER), OrderStatus.DELIVERED);
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(delivered);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

        assertThrows(InvalidOperationException.class,
                () -> paymentService.processPayment(3L, true, sessionRequest(1L, Role.CUSTOMER)));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderService, never()).markOrderAsPaid(any(Order.class));
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
    }

    @Test
    void processPayment_success_marksOrderPaid() {
        Order own = order(9L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        Payment payment = new Payment();
        payment.setPaymentId(3L);
        payment.setOrder(own);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.processPayment(3L, false, sessionRequest(1L, Role.CUSTOMER));

        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        verify(orderService).markOrderAsPaid(own);
    }

    @Test
    void failedPayment_leavesOrderUnpaid_andRetryViaNewPaymentIsAllowed() {
        Order own = order(9L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        Payment failed = new Payment();
        failed.setPaymentId(3L);
        failed.setOrder(own);
        failed.setPaymentStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(failed));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse failedResponse = paymentService.processPayment(3L, true, sessionRequest(1L, Role.CUSTOMER));

        assertEquals(PaymentStatus.FAILED, failedResponse.getPaymentStatus());
        verify(orderService, never()).markOrderAsPaid(any(Order.class));

        // Retry: because the order is still live, a NEW payment may be created.
        when(orderService.findOrderOrThrow(9L)).thenReturn(own);
        PaymentResponse retry = paymentService.createPayment(paymentRequest(9L), sessionRequest(1L, Role.CUSTOMER));

        assertEquals(9L, retry.getOrderId());
        assertEquals(PaymentStatus.PENDING, retry.getPaymentStatus());
    }
}
