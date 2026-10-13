package com.giftconnect.service;

import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;
import com.giftconnect.dto.OrderResponse;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.*;
import com.giftconnect.exception.DuplicateResourceException;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.OrderIdempotencyRepository;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.util.AccessControl;
import com.giftconnect.util.OrderFingerprint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final ActivityLogService activityLogService;

    /** Orders in these states can no longer be changed or cancelled. */
    private static final Set<OrderStatus> LOCKED_STATUSES = Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    /**
     * Phase 4I — the client Idempotency-Key is bounded to the
     * order_idempotency.idempotency_key column width (VARCHAR(100)).
     */
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    /** Allowed Idempotency-Key characters: an opaque, header-safe token. */
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");

    /**
     * Forward-only fulfillment progression. A status change must move to a
     * LATER stage, so a caller can never walk an order backwards or jump out of
     * a terminal state. CANCELLED is intentionally NOT part of this flow: it is
     * reached only through the cancel endpoint, which restocks the items.
     */
    private static final List<OrderStatus> FULFILLMENT_FLOW = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED);

    @Autowired
    public OrderService(OrderRepository orderRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderIdempotencyRepository orderIdempotencyRepository,
                         ActivityLogService activityLogService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderIdempotencyRepository = orderIdempotencyRepository;
        this.activityLogService = activityLogService;
    }

    /**
     * Creates an order: validates the user and every product/quantity,
     * snapshots each product's current price into its OrderItem, reduces
     * stock, and computes the order total — all in one transaction, so a
     * failure partway through (e.g. item 2 of 3 is out of stock) rolls
     * back item 1's stock reduction too.
     *
     * Phase 4I — {@code idempotencyKey} is the optional client-supplied
     * Idempotency-Key header. When null, behavior is unchanged. When present,
     * a replay of the same key returns the original order untouched, and a key
     * reused with different contents is rejected (409).
     *
     * Concurrent-request behavior: a sequential matching retry returns the
     * existing order. Two requests racing on the same key are arbitrated by the
     * UNIQUE (user_id, idempotency_key) constraint; the losing request may
     * receive a retryable 409 and must retry with the same key. The losing
     * transaction rolls back, so no duplicate order or duplicate stock
     * deduction persists. Note that mock-based tests do not prove real MySQL
     * concurrency behavior.
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String idempotencyKey, HttpServletRequest httpRequest) {
        // The order owner is ALWAYS the authenticated caller, resolved from the
        // server-side session — never a client-supplied id (OrderRequest has no
        // userId at all), so a customer cannot place an order as someone else.
        Long actorId = AccessControl.requireActorId(httpRequest);
        User user = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + actorId));

        // Phase 4I — optional idempotency. The key is scoped to the authenticated
        // user; the fingerprint is derived from the canonical request contents.
        String key = null;
        String fingerprint = null;
        if (idempotencyKey != null) {
            key = validateIdempotencyKey(idempotencyKey);
            fingerprint = OrderFingerprint.of(request);

            Optional<OrderIdempotency> existing =
                    orderIdempotencyRepository.findByUser_UserIdAndIdempotencyKey(actorId, key);
            if (existing.isPresent()) {
                OrderIdempotency record = existing.get();
                if (!record.getFingerprint().equals(fingerprint)) {
                    // Same key, different order contents: refuse rather than
                    // silently returning an unrelated order.
                    throw new DuplicateResourceException(
                            "Idempotency-Key was already used for a different order request");
                }
                // Matching replay: return the order created for this key. No new
                // order is saved and no stock is touched.
                return OrderResponse.fromEntity(record.getOrder());
            }
        }

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus("PENDING");

        List<OrderItemRequest> items = request.getItems();

        // Validate every quantity up front, in request order, so an invalid
        // quantity is still rejected before any product row is locked.
        for (OrderItemRequest itemRequest : items) {
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new InvalidOperationException(
                        "Quantity must be greater than 0 for product id: " + itemRequest.getProductId());
            }
        }

        // Phase 4G — lock every requested product row BEFORE reading its stock.
        // findByIdForUpdate runs a SELECT ... FOR UPDATE inside this transaction,
        // so the stock read/check/decrement below cannot be overtaken by a
        // concurrent checkout or cancellation. Product rows are locked in ascending
        // product-id order, so two orders that touch overlapping products acquire
        // the same locks in the same order and therefore cannot deadlock.
        Map<Long, Product> lockedProducts = new LinkedHashMap<>();
        items.stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .sorted(Comparator.nullsFirst(Comparator.<Long>naturalOrder()))
                .forEach(productId -> lockedProducts.put(productId,
                        productRepository.findByIdForUpdate(productId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "Product not found with id: " + productId))));

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : items) {
            Product product = lockedProducts.get(itemRequest.getProductId());

            // Only ACTIVE products may be ordered. The catalog already hides INACTIVE
            // products from customers, but this server-side check is the real guard —
            // a client could otherwise POST an INACTIVE product id directly. A null
            // status is NOT treated as ACTIVE (rejected), so legacy/blank data stays
            // safe by default.
            if (!"ACTIVE".equals(product.getStatus())) {
                throw new InvalidOperationException(
                        "Product '" + product.getProductName() + "' is not available for ordering");
            }

            if (product.getStock() < itemRequest.getQuantity()) {
                throw new InvalidOperationException(
                        "Insufficient stock for product '" + product.getProductName() + "' — " +
                                "requested " + itemRequest.getQuantity() + ", available " + product.getStock());
            }

            // Snapshot the price NOW, before it's saved on the order item —
            // this is deliberately independent of Product's price later changing.
            BigDecimal itemPrice = product.getPrice();

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(itemPrice);
            order.addOrderItem(orderItem);

            total = total.add(itemPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity())));

            // Reduce stock on the LOCKED row (see the lock acquisition above), so
            // the decrement is based on the value read under the lock.
            product.setStock(product.getStock() - itemRequest.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        // Phase 4I — persist the idempotency record in the SAME transaction as the
        // order and its stock changes, so a rolled-back order never leaves a key
        // without its order. saveAndFlush forces the UNIQUE (user_id,
        // idempotency_key) constraint to be checked now rather than at commit.
        if (key != null) {
            OrderIdempotency record = new OrderIdempotency();
            record.setUser(user);
            record.setIdempotencyKey(key);
            record.setFingerprint(fingerprint);
            record.setOrder(saved);
            try {
                orderIdempotencyRepository.saveAndFlush(record);
            } catch (DataIntegrityViolationException e) {
                // A concurrent request with the same (user, key) won the race on the
                // unique constraint. Abort THIS transaction — do NOT read or continue
                // inside a rollback-only transaction. The whole creation (order +
                // stock) rolls back, so no duplicate order is kept. A subsequent
                // retry finds the winner's record and is served its order.
                throw new DuplicateResourceException(
                        "A concurrent order with this Idempotency-Key is already being processed; please retry");
            }
        }

        activityLogService.log(ActivityType.ORDER_CREATED, ActivityStatus.SUCCESS,
                httpRequest, "ORDER", saved.getOrderId(),
                "Order placed with " + request.getItems().size() + " item(s), total " + total);
        return OrderResponse.fromEntity(saved);
    }

    /**
     * Phase 4I — validates a supplied Idempotency-Key: non-blank, at most
     * {@link #MAX_IDEMPOTENCY_KEY_LENGTH} characters, and limited to a
     * conservative header-safe charset. A violation is an invalid request (400).
     */
    private String validateIdempotencyKey(String rawKey) {
        if (rawKey.isBlank()) {
            throw new InvalidOperationException("Idempotency-Key must not be blank");
        }
        if (rawKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidOperationException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(rawKey).matches()) {
            throw new InvalidOperationException(
                    "Idempotency-Key contains unsupported characters (allowed: A-Z a-z 0-9 . _ : -)");
        }
        return rawKey;
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderResponse getOrderById(Long id, HttpServletRequest httpRequest) {
        Order order = findOrderOrThrow(id);
        // Only the order's owner or an ADMIN may read it — 403 otherwise (never
        // 404), so a customer cannot probe which order ids belong to others.
        AccessControl.requireOwnerOrAdmin(order.getUser().getUserId(), httpRequest);
        return OrderResponse.fromEntity(order);
    }

    public List<OrderResponse> getOrdersByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return orderRepository.findByUser_UserId(userId).stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderResponse updateOrderStatus(Long id, String statusValue, HttpServletRequest httpRequest) {
        Order order = findOrderOrThrow(id);
        // Fulfillment progression is an operator action: only an ADMIN may move an
        // order through its lifecycle. Owning the order is deliberately NOT enough —
        // otherwise an owning CUSTOMER could mark their own order CONFIRMED/SHIPPED/
        // DELIVERED. Enforced here on the server, independent of the (hidden) UI.
        AccessControl.requireAdmin(httpRequest);

        if (LOCKED_STATUSES.contains(order.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot change the status of an order that is already " + order.getStatus());
        }

        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus = parseStatus(statusValue);
        validateTransition(previousStatus, newStatus);
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        // Audit the ACTING user (resolved from the session) rather than the order's
        // owner. The order and its owner stay identifiable through entityType/entityId.
        activityLogService.log(ActivityType.ORDER_STATUS_CHANGED, ActivityStatus.SUCCESS,
                httpRequest, "ORDER", saved.getOrderId(),
                "Order status changed from " + previousStatus + " to " + newStatus);
        return OrderResponse.fromEntity(saved);
    }

    /**
     * Cancels an order and restocks every item — the inverse of createOrder's
     * stock reduction. Delivered or already-cancelled orders cannot be cancelled.
     */
    @Transactional
    public OrderResponse cancelOrder(Long id, HttpServletRequest httpRequest) {
        Order order = findOrderOrThrow(id);
        // Only the owner or an ADMIN may cancel an order.
        AccessControl.requireOwnerOrAdmin(order.getUser().getUserId(), httpRequest);

        if (LOCKED_STATUSES.contains(order.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot cancel an order that is already " + order.getStatus());
        }

        // Phase 4G — restock through the same row lock used at checkout, taking the
        // locks in ascending product-id order. This prevents a cancellation from
        // overwriting a concurrent inventory change (a lost update) and avoids
        // deadlocks against an in-flight order for an overlapping product set.
        List<OrderItem> items = new ArrayList<>(order.getOrderItems());
        items.sort(Comparator.comparing(
                (OrderItem item) -> item.getProduct().getProductId(),
                Comparator.nullsFirst(Comparator.<Long>naturalOrder())));

        for (OrderItem item : items) {
            Long productId = item.getProduct().getProductId();
            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + productId));
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        // Same rule as updateOrderStatus: record who actually cancelled it.
        activityLogService.log(ActivityType.ORDER_CANCELLED, ActivityStatus.SUCCESS,
                httpRequest, "ORDER", saved.getOrderId(),
                "Order cancelled, items restocked");
        return OrderResponse.fromEntity(saved);
    }

    /** Called by PaymentService after a successful simulated payment. */
    @Transactional
    public void markOrderAsPaid(Order order) {
        order.setPaymentStatus("PAID");
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);
    }

    Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Invalid order status: " + value);
        }
    }

    /**
     * Enforces forward-only fulfillment transitions. This is a real state check
     * (not an authentication check), so an order can never move backwards, and
     * CANCELLED must go through cancelOrder so its stock is restored.
     */
    private void validateTransition(OrderStatus current, OrderStatus target) {
        if (target == OrderStatus.CANCELLED) {
            throw new InvalidOperationException(
                    "Use the cancel endpoint to cancel an order (it restocks the items)");
        }

        int from = FULFILLMENT_FLOW.indexOf(current);
        int to = FULFILLMENT_FLOW.indexOf(target);
        if (from < 0 || to < 0) {
            throw new InvalidOperationException(
                    "Unsupported order status transition: " + current + " to " + target);
        }
        if (to == from) {
            throw new InvalidOperationException("Order is already " + current);
        }
        if (to < from) {
            throw new InvalidOperationException(
                    "Cannot move an order backwards from " + current + " to " + target);
        }
    }
}
