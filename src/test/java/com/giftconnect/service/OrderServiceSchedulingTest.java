package com.giftconnect.service;

import com.giftconnect.config.TimeConfig;
import com.giftconnect.controller.AuthController;
import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;
import com.giftconnect.dto.OrderResponse;
import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderStatus;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.repository.OrderIdempotencyRepository;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests for the signature feature end to end inside
 * OrderService: when a schedule is requested, the REAL DeliveryPolicy (pinned to
 * a fixed Clock) is applied during createOrder, and the refusal happens before
 * any product is locked or any order is written.
 *
 * The clock is fixed to 2026-10-01 (Asia/Kolkata), so the earliest eligible
 * delivery date is exactly 2026-10-08.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceSchedulingTest {

    private static final ZoneId ZONE = TimeConfig.DELIVERY_ZONE;
    private static final LocalDate TODAY = LocalDate.parse("2026-10-01");
    private static final LocalDate EARLIEST = LocalDate.parse("2026-10-08");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderIdempotencyRepository orderIdempotencyRepository;
    @Mock
    private ActivityLogService activityLogService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Instant fixed = TODAY.atTime(10, 0).atZone(ZONE).toInstant();
        DeliveryPolicy realPolicy = new DeliveryPolicy(Clock.fixed(fixed, ZONE));
        orderService = new OrderService(orderRepository, productRepository, userRepository,
                orderIdempotencyRepository, activityLogService, realPolicy);
    }

    private HttpServletRequest sessionRequest(Long userId, Role role) {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpSession session = org.mockito.Mockito.mock(HttpSession.class);
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_ID)).thenReturn(userId);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_ROLE)).thenReturn(role);
        lenient().when(session.getAttribute(AuthController.SESSION_USER_EMAIL)).thenReturn("actor@example.com");
        return request;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setUserId(id);
        user.setRole(role);
        user.setEmail("user" + id + "@example.com");
        return user;
    }

    private Product activeProduct(Long id, String price, int stock) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName("Gift " + id);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setStatus("ACTIVE");
        return product;
    }

    private OrderRequest scheduledRequest(LocalDate date, String slot) {
        OrderRequest request = new OrderRequest();
        request.setShippingAddress("addr");
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(1);
        request.setItems(List.of(item));
        request.setDeliveryDate(date);
        request.setDeliverySlot(slot);
        return request;
    }

    private void stubCheckoutWrites(User actor, Product product) {
        when(userRepository.findById(actor.getUserId())).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(product.getProductId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void scheduledOrder_persistsRequestedScheduleTimezoneAndGiftDetails() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = activeProduct(10L, "10.00", 5);
        stubCheckoutWrites(actor, product);

        OrderRequest request = scheduledRequest(EARLIEST, "09:00-12:00");
        request.setRecipientName("  Ravi  ");
        request.setGiftMessage("  Happy Birthday  ");

        OrderResponse response =
                orderService.createOrder(request, null, sessionRequest(1L, Role.CUSTOMER));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        Order order = saved.getValue();
        assertEquals(EARLIEST, order.getRequestedDeliveryDate());
        assertEquals("09:00-12:00", order.getRequestedTimeSlot(), "the stable slot code must be stored");
        assertEquals("Asia/Kolkata", order.getDeliveryTimezone());
        assertEquals("Ravi", order.getRecipientName(), "recipient name should be trimmed");
        assertEquals("Happy Birthday", order.getGiftMessage(), "gift message should be trimmed");
        assertNull(order.getActualDeliveryDate(), "actual delivery is only set when the order is DELIVERED");

        assertEquals(EARLIEST, response.getRequestedDeliveryDate());
        assertEquals("09:00-12:00", response.getRequestedTimeSlot());
        assertEquals("Asia/Kolkata", response.getDeliveryTimezone());
    }

    @Test
    void sixDaysAhead_isRejectedBeforeAnyLockOrWrite() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(
                        scheduledRequest(LocalDate.parse("2026-10-07"), "09:00-12:00"),
                        null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).findByIdForUpdate(any());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void sameDay_isRejected() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(
                        scheduledRequest(TODAY, "09:00-12:00"),
                        null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void unknownSlot_isRejected() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(
                        scheduledRequest(EARLIEST, "MIDNIGHT"),
                        null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void slotWithoutDate_isRejected() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(
                        scheduledRequest(null, "09:00-12:00"),
                        null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void dateWithoutSlot_isRejected() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(
                        scheduledRequest(EARLIEST, null),
                        null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void nonScheduledOrder_leavesAllScheduleFieldsNull() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = activeProduct(10L, "10.00", 5);
        stubCheckoutWrites(actor, product);

        OrderRequest request = new OrderRequest();
        request.setShippingAddress("addr");
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        OrderResponse response =
                orderService.createOrder(request, null, sessionRequest(1L, Role.CUSTOMER));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertNull(saved.getValue().getRequestedDeliveryDate());
        assertNull(saved.getValue().getRequestedTimeSlot());
        assertNull(saved.getValue().getDeliveryTimezone());
        assertNull(response.getRequestedDeliveryDate());
    }

    @Test
    void movingOrderToDelivered_stampsActualDeliveryDateFromClock() {
        User owner = user(2L, Role.CUSTOMER);
        Order existing = new Order();
        existing.setOrderId(5L);
        existing.setUser(owner);
        existing.setStatus(OrderStatus.SHIPPED);
        existing.setPaymentStatus("PAID");
        existing.setShippingAddress("addr");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response =
                orderService.updateOrderStatus(5L, "DELIVERED", sessionRequest(9L, Role.ADMIN));

        LocalDateTime expected = TODAY.atTime(10, 0);
        assertEquals(OrderStatus.DELIVERED, response.getStatus());
        assertEquals(expected, existing.getActualDeliveryDate());
        assertEquals(expected, response.getActualDeliveryDate());
    }
}
