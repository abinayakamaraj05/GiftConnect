package com.giftconnect.service;

import com.giftconnect.controller.AuthController;
import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;
import com.giftconnect.dto.OrderResponse;
import com.giftconnect.entity.Order;
import com.giftconnect.entity.OrderIdempotency;
import com.giftconnect.entity.OrderItem;
import com.giftconnect.entity.OrderStatus;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.AccessDeniedException;
import com.giftconnect.exception.DuplicateResourceException;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.repository.OrderIdempotencyRepository;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.util.OrderFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused authorization + state-machine tests for OrderService. Repositories and
 * the audit service are mocked, so nothing touches the database or SMTP.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceAuthTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private OrderIdempotencyRepository orderIdempotencyRepository;
    // New dependency for scheduled delivery. These tests never send a delivery
    // date, so the scheduling branch is skipped and this mock is never invoked —
    // it exists only to satisfy OrderService's constructor.
    @Mock
    private DeliveryPolicy deliveryPolicy;

    @InjectMocks
    private OrderService orderService;

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

    private Order order(Long id, User owner, OrderStatus status) {
        Order order = new Order();
        order.setOrderId(id);
        order.setUser(owner);
        order.setStatus(status);
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    // ------------------------------------------------------------------
    // createOrder — owner is the session user, never a body field
    // ------------------------------------------------------------------

    @Test
    void createOrder_bindsOwnerToSessionUser() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequest request = new OrderRequest();
        request.setShippingAddress("addr");
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        OrderResponse response = orderService.createOrder(request, null, sessionRequest(1L, Role.CUSTOMER));

        assertEquals(1L, response.getUserId());
        assertEquals(3, product.getStock(), "stock should be reduced by the ordered quantity");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_withoutSessionActor_isDenied() {
        OrderRequest request = new OrderRequest();
        request.setItems(List.of());

        assertThrows(AccessDeniedException.class,
                () -> orderService.createOrder(request, null, sessionRequest(null, null)));
    }

    private OrderRequest requestForProduct(Long productId, Integer quantity) {
        OrderRequest request = new OrderRequest();
        request.setShippingAddress("addr");
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    @Test
    void createOrder_rejectsInactiveProduct_beforeAnyMutation() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("INACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 2), null, sessionRequest(1L, Role.CUSTOMER)));

        // Nothing was saved and the stock was NOT decremented.
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
        assertEquals(5, product.getStock(), "stock must be untouched when the product is rejected");
    }

    @Test
    void createOrder_rejectsProductWithNullStatus() {
        // A null status is not "ACTIVE": legacy/blank data is rejected, not sold.
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 1), null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
        assertEquals(5, product.getStock());
    }

    @Test
    void createOrder_rejectsInsufficientStock() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(1);
        product.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 2), null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
        assertEquals(1, product.getStock(), "stock must be untouched when the order is rejected");
    }

    @Test
    void createOrder_rejectsNonPositiveQuantity() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 0), null, sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createOrder_usesLockingLookupBeforeDecrementingStock() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(requestForProduct(10L, 2), null, sessionRequest(1L, Role.CUSTOMER));

        // Phase 4G — stock is read under the FOR UPDATE lock; the unlocked
        // findById must never be used on the checkout path.
        verify(productRepository).findByIdForUpdate(10L);
        verify(productRepository, never()).findById(any());
        assertEquals(3, product.getStock());
    }

    @Test
    void createOrder_locksMultipleProductsInAscendingIdOrder() {
        User actor = user(1L, Role.CUSTOMER);
        Product high = new Product();
        high.setProductId(30L);
        high.setProductName("Third");
        high.setPrice(new BigDecimal("3.00"));
        high.setStock(5);
        high.setStatus("ACTIVE");
        Product low = new Product();
        low.setProductId(10L);
        low.setProductName("First");
        low.setPrice(new BigDecimal("1.00"));
        low.setStock(5);
        low.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(low));
        when(productRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(high));
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Request lists product 30 before product 10 — locks must still be
        // acquired 10 then 30 so concurrent orders cannot deadlock.
        OrderRequest request = new OrderRequest();
        request.setShippingAddress("addr");
        OrderItemRequest highItem = new OrderItemRequest();
        highItem.setProductId(30L);
        highItem.setQuantity(1);
        OrderItemRequest lowItem = new OrderItemRequest();
        lowItem.setProductId(10L);
        lowItem.setQuantity(2);
        request.setItems(List.of(highItem, lowItem));

        OrderResponse response =
                orderService.createOrder(request, null, sessionRequest(1L, Role.CUSTOMER));

        InOrder lockOrder = inOrder(productRepository);
        lockOrder.verify(productRepository).findByIdForUpdate(10L);
        lockOrder.verify(productRepository).findByIdForUpdate(30L);

        // Association preserved: both items saved with their requested quantities.
        verify(orderRepository).save(savedOrder.capture());
        assertEquals(2, savedOrder.getValue().getOrderItems().size());
        assertEquals(new BigDecimal("5.00"), response.getTotalAmount());
    }

    // ------------------------------------------------------------------
    // createOrder — Phase 4I server-side idempotency. These are MOCK-BASED
    // tests: they verify service behavior under a mocked OrderIdempotency-
    // Repository only. They do NOT prove real MySQL unique-constraint or
    // concurrency behavior.
    // ------------------------------------------------------------------

    @Test
    void createOrder_withoutKey_retainsExistingBehavior() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(requestForProduct(10L, 2), null,
                sessionRequest(1L, Role.CUSTOMER));

        assertEquals(1L, response.getUserId());
        assertEquals(3, product.getStock());
        verify(orderRepository).save(any(Order.class));
        // No key -> the idempotency table is never touched.
        verify(orderIdempotencyRepository, never()).findByUser_UserIdAndIdempotencyKey(any(), any());
        verify(orderIdempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrder_withKey_persistsIdempotencyRecordForFirstRequest() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");
        OrderRequest request = requestForProduct(10L, 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.empty());
        when(orderIdempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "checkout-123",
                sessionRequest(1L, Role.CUSTOMER));

        assertEquals(1L, response.getUserId());
        assertEquals(3, product.getStock());
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        ArgumentCaptor<OrderIdempotency> storedRecord = ArgumentCaptor.forClass(OrderIdempotency.class);
        verify(orderIdempotencyRepository).saveAndFlush(storedRecord.capture());
        assertEquals("checkout-123", storedRecord.getValue().getIdempotencyKey());
        assertEquals(OrderFingerprint.of(request), storedRecord.getValue().getFingerprint());
        assertEquals(1L, storedRecord.getValue().getUser().getUserId());
        assertEquals(savedOrder.getValue(), storedRecord.getValue().getOrder(),
                "the idempotency record must reference the order saved by this call");
    }

    @Test
    void createOrder_matchingReplay_returnsOriginalOrderWithNoWrites() {
        User actor = user(1L, Role.CUSTOMER);
        Order existing = order(75L, actor, OrderStatus.PENDING);
        OrderRequest request = requestForProduct(10L, 2);
        OrderIdempotency record = new OrderIdempotency();
        record.setIdempotencyId(1L);
        record.setUser(actor);
        record.setIdempotencyKey("checkout-123");
        record.setFingerprint(OrderFingerprint.of(request));
        record.setOrder(existing);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.of(record));

        // No orderRepository/productRepository stubs: the replay path must not
        // create an order, lock products, or decrement stock.
        OrderResponse response = orderService.createOrder(request, "checkout-123",
                sessionRequest(1L, Role.CUSTOMER));

        assertEquals(75L, response.getOrderId());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).findByIdForUpdate(any());
        verify(productRepository, never()).save(any(Product.class));
        verify(orderIdempotencyRepository, never()).saveAndFlush(any(OrderIdempotency.class));
    }

    @Test
    void createOrder_sameKeyDifferentPayload_throwsConflict() {
        User actor = user(1L, Role.CUSTOMER);
        Order existing = order(75L, actor, OrderStatus.PENDING);
        OrderIdempotency record = new OrderIdempotency();
        record.setIdempotencyId(1L);
        record.setUser(actor);
        record.setIdempotencyKey("checkout-123");
        record.setFingerprint(OrderFingerprint.of(requestForProduct(10L, 2)));
        record.setOrder(existing);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.of(record));

        // A DIFFERENT payload (product 30 instead of product 10) reuses the key.
        assertThrows(DuplicateResourceException.class,
                () -> orderService.createOrder(requestForProduct(30L, 1), "checkout-123",
                        sessionRequest(1L, Role.CUSTOMER)));

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderIdempotencyRepository, never()).saveAndFlush(any(OrderIdempotency.class));
    }

    @Test
    void createOrder_sameKeyDifferentUser_isIndependentlyScoped() {
        User firstUser = user(1L, Role.CUSTOMER);
        User secondUser = user(9L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");
        Order firstOrder = order(75L, firstUser, OrderStatus.PENDING);
        OrderIdempotency firstUsersRecord = new OrderIdempotency();
        firstUsersRecord.setIdempotencyId(1L);
        firstUsersRecord.setUser(firstUser);
        firstUsersRecord.setIdempotencyKey("checkout-123");
        firstUsersRecord.setFingerprint(OrderFingerprint.of(requestForProduct(10L, 2)));
        firstUsersRecord.setOrder(firstOrder);

        when(userRepository.findById(9L)).thenReturn(Optional.of(secondUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderIdempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(9L, "checkout-123"))
                .thenReturn(Optional.empty());
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.of(firstUsersRecord));

        // User 9 reusing user 1's key is a fresh attempt, not a replay.
        OrderResponse secondOrder = orderService.createOrder(requestForProduct(10L, 2), "checkout-123",
                sessionRequest(9L, Role.CUSTOMER));
        assertEquals(9L, secondOrder.getUserId());
        assertEquals(3, product.getStock());
        verify(orderRepository).save(any(Order.class));

        // User 1 keeps their own replay of the same key.
        OrderResponse replay = orderService.createOrder(requestForProduct(10L, 2), "checkout-123",
                sessionRequest(1L, Role.CUSTOMER));
        assertEquals(75L, replay.getOrderId());

        // Each lookup was scoped to the session actor, never shared.
        verify(orderIdempotencyRepository).findByUser_UserIdAndIdempotencyKey(9L, "checkout-123");
        verify(orderIdempotencyRepository).findByUser_UserIdAndIdempotencyKey(1L, "checkout-123");
    }

    @Test
    void createOrder_rejectsBlankKey() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 1), "   ",
                        sessionRequest(1L, Role.CUSTOMER)));
        verify(orderIdempotencyRepository, never()).findByUser_UserIdAndIdempotencyKey(any(), any());
    }

    @Test
    void createOrder_rejectsOverlongKey() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 1), "k".repeat(101),
                        sessionRequest(1L, Role.CUSTOMER)));
        verify(orderIdempotencyRepository, never()).findByUser_UserIdAndIdempotencyKey(any(), any());
    }

    @Test
    void createOrder_rejectsMalformedKey() {
        User actor = user(1L, Role.CUSTOMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThrows(InvalidOperationException.class,
                () -> orderService.createOrder(requestForProduct(10L, 1), "key with spaces!",
                        sessionRequest(1L, Role.CUSTOMER)));
        verify(orderIdempotencyRepository, never()).findByUser_UserIdAndIdempotencyKey(any(), any());
    }

    @Test
    void createOrder_persistsOrderBeforeIdempotencyRecordInSameWriteOrder() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.empty());
        when(orderIdempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(requestForProduct(10L, 2), "checkout-123",
                sessionRequest(1L, Role.CUSTOMER));

        // MOCK-BASED: reflects the same-transaction sequence in createOrder —
        // stock save and order save both precede the idempotency record write.
        InOrder writes = inOrder(productRepository, orderRepository, orderIdempotencyRepository);
        writes.verify(productRepository).save(any(Product.class));
        writes.verify(orderRepository).save(any(Order.class));
        writes.verify(orderIdempotencyRepository).saveAndFlush(any(OrderIdempotency.class));
        assertEquals(3, product.getStock());
    }

    @Test
    void createOrder_uniqueConstraintViolation_isTranslatedToRetryableConflict() {
        User actor = user(1L, Role.CUSTOMER);
        Product product = new Product();
        product.setProductId(10L);
        product.setProductName("Mug");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setStatus("ACTIVE");
        OrderRequest request = requestForProduct(10L, 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(1L, "checkout-123"))
                .thenReturn(Optional.empty());
        // A SECOND request wins the race and inserts the key; this request's
        // insert hits the unique constraint. Simulated — NOT a real MySQL race.
        when(orderIdempotencyRepository.saveAndFlush(any(OrderIdempotency.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key", new RuntimeException("race")));

        assertThrows(DuplicateResourceException.class,
                () -> orderService.createOrder(request, "checkout-123",
                        sessionRequest(1L, Role.CUSTOMER)));

        // The losing transaction makes no further idempotency writes and does
        // NOT claim its request won: the caller must retry with the same key.
        verify(orderIdempotencyRepository).saveAndFlush(any(OrderIdempotency.class));
        // MOCK NOTE: no rollback occurs in a mocked test; whether no duplicate
        // order/stock deduction persists is a transaction concern only a real
        // DB test (out of scope for this phase) could prove.
    }

    // ------------------------------------------------------------------
    // getOrderById — owner or ADMIN only
    // ------------------------------------------------------------------

    @Test
    void getOrderById_deniesOtherCustomer() {
        Order existing = order(50L, user(2L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> orderService.getOrderById(50L, sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void getOrderById_allowsOwner() {
        Order existing = order(50L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.getOrderById(50L, sessionRequest(1L, Role.CUSTOMER));

        assertEquals(50L, response.getOrderId());
    }

    @Test
    void getOrderById_allowsAdmin() {
        Order existing = order(50L, user(2L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.getOrderById(50L, sessionRequest(1L, Role.ADMIN));

        assertEquals(50L, response.getOrderId());
    }

    // ------------------------------------------------------------------
    // updateOrderStatus — ADMIN-only fulfillment + forward-only transitions
    // ------------------------------------------------------------------

    @Test
    void updateOrderStatus_deniesNonOwnerCustomer() {
        Order existing = order(5L, user(2L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> orderService.updateOrderStatus(5L, "CONFIRMED", sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void updateOrderStatus_deniesOwningCustomerForValidTransition() {
        // The owner must NOT be able to advance their own order, even to the very
        // next valid stage — fulfillment is an operator (ADMIN) action.
        Order existing = order(5L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> orderService.updateOrderStatus(5L, "CONFIRMED", sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void updateOrderStatus_deniesOwningCustomerMarkingDelivered() {
        Order existing = order(5L, user(1L, Role.CUSTOMER), OrderStatus.SHIPPED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> orderService.updateOrderStatus(5L, "DELIVERED", sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void updateOrderStatus_allowsAdminForwardTransition() {
        Order existing = order(5L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(5L, "CONFIRMED", sessionRequest(9L, Role.ADMIN));

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
    }

    @Test
    void updateOrderStatus_allowsAdminOnOtherCustomersOrder() {
        Order existing = order(5L, user(2L, Role.CUSTOMER), OrderStatus.PROCESSING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(5L, "SHIPPED", sessionRequest(9L, Role.ADMIN));

        assertEquals(OrderStatus.SHIPPED, response.getStatus());
    }

    @Test
    void updateOrderStatus_rejectsBackwardsMove() {
        Order existing = order(5L, user(1L, Role.CUSTOMER), OrderStatus.SHIPPED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidOperationException.class,
                () -> orderService.updateOrderStatus(5L, "CONFIRMED", sessionRequest(9L, Role.ADMIN)));
    }

    @Test
    void updateOrderStatus_rejectsCancelTarget() {
        Order existing = order(5L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidOperationException.class,
                () -> orderService.updateOrderStatus(5L, "CANCELLED", sessionRequest(9L, Role.ADMIN)));
    }

    // ------------------------------------------------------------------
    // cancelOrder — owner/admin; restocks on success
    // ------------------------------------------------------------------

    @Test
    void cancelOrder_deniesNonOwner() {
        Order existing = order(6L, user(2L, Role.CUSTOMER), OrderStatus.PENDING);
        when(orderRepository.findById(6L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> orderService.cancelOrder(6L, sessionRequest(1L, Role.CUSTOMER)));
    }

    @Test
    void cancelOrder_restocksForOwner() {
        Product product = new Product();
        product.setProductId(10L);
        product.setStock(5);

        Order existing = order(6L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("10.00"));
        existing.addOrderItem(item);

        when(orderRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(6L, sessionRequest(1L, Role.CUSTOMER));

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals(7, product.getStock(), "cancelling should restore the ordered quantity");
        verify(productRepository).findByIdForUpdate(10L);
        verify(productRepository).save(product);
    }

    @Test
    void cancelOrder_usesLockingLookupForRestock() {
        Product product = new Product();
        product.setProductId(10L);
        product.setStock(5);

        Order existing = order(6L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("10.00"));
        existing.addOrderItem(item);

        when(orderRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelOrder(6L, sessionRequest(1L, Role.CUSTOMER));

        // Phase 4G — restock reads the row under FOR UPDATE; the unlocked
        // findById must never be used on the cancellation path.
        verify(productRepository).findByIdForUpdate(10L);
        verify(productRepository, never()).findById(any());
        assertEquals(7, product.getStock());
    }

    @Test
    void cancelOrder_locksMultipleProductsInAscendingIdOrder() {
        Product high = new Product();
        high.setProductId(30L);
        high.setStock(1);
        Product low = new Product();
        low.setProductId(10L);
        low.setStock(1);

        Order existing = order(6L, user(1L, Role.CUSTOMER), OrderStatus.PENDING);
        OrderItem highItem = new OrderItem();
        highItem.setProduct(high);
        highItem.setQuantity(2);
        highItem.setPrice(new BigDecimal("3.00"));
        existing.addOrderItem(highItem);
        OrderItem lowItem = new OrderItem();
        lowItem.setProduct(low);
        lowItem.setQuantity(1);
        lowItem.setPrice(new BigDecimal("1.00"));
        existing.addOrderItem(lowItem);

        when(orderRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(low));
        when(productRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(high));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelOrder(6L, sessionRequest(1L, Role.CUSTOMER));

        InOrder lockOrder = inOrder(productRepository);
        lockOrder.verify(productRepository).findByIdForUpdate(10L);
        lockOrder.verify(productRepository).findByIdForUpdate(30L);

        assertEquals(2, low.getStock(), "restock preserves quantity per product");
        assertEquals(3, high.getStock());
    }
}
