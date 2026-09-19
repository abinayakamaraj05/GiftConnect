package com.giftconnect.service;

import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;
import com.giftconnect.dto.OrderResponse;
import com.giftconnect.entity.*;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /** Orders in these states can no longer be changed or cancelled. */
    private static final Set<OrderStatus> LOCKED_STATUSES = Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    @Autowired
    public OrderService(OrderRepository orderRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates an order: validates the user and every product/quantity,
     * snapshots each product's current price into its OrderItem, reduces
     * stock, and computes the order total — all in one transaction, so a
     * failure partway through (e.g. item 2 of 3 is out of stock) rolls
     * back item 1's stock reduction too.
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getUserId()));

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus("PENDING");

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new InvalidOperationException(
                        "Quantity must be greater than 0 for product id: " + itemRequest.getProductId());
            }

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + itemRequest.getProductId()));

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

            // Reduce stock immediately (this is a simple MVP — no separate
            // "reserved stock" concept, which would be needed for a
            // production system handling concurrent checkouts).
            product.setStock(product.getStock() - itemRequest.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        return OrderResponse.fromEntity(saved);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderResponse getOrderById(Long id) {
        return OrderResponse.fromEntity(findOrderOrThrow(id));
    }

    public List<OrderResponse> getOrdersByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return orderRepository.findByUser_UserId(userId).stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderResponse updateOrderStatus(Long id, String statusValue) {
        Order order = findOrderOrThrow(id);

        if (LOCKED_STATUSES.contains(order.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot change the status of an order that is already " + order.getStatus());
        }

        OrderStatus newStatus = parseStatus(statusValue);
        order.setStatus(newStatus);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    /**
     * Cancels an order and restocks every item — the inverse of createOrder's
     * stock reduction. Delivered or already-cancelled orders cannot be cancelled.
     */
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = findOrderOrThrow(id);

        if (LOCKED_STATUSES.contains(order.getStatus())) {
            throw new InvalidOperationException(
                    "Cannot cancel an order that is already " + order.getStatus());
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.fromEntity(orderRepository.save(order));
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
}
