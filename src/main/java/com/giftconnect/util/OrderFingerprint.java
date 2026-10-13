package com.giftconnect.util;

import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 4I — produces a deterministic SHA-256 fingerprint of the meaningful
 * content of an order request, used to tell a retry of the SAME request from a
 * reuse of the same Idempotency-Key with DIFFERENT contents.
 *
 * Canonicalization rules (deliberately minimal — differences that would change
 * the actual order are preserved):
 *   - shippingAddress is used verbatim (not trimmed or otherwise normalized),
 *     because it is stored and delivered exactly as supplied.
 *   - items are compared order-insensitively: they are sorted by productId,
 *     then by quantity (a tiebreak only, for deterministic output when the
 *     same product appears on more than one line).
 *   - each line contributes its productId and quantity; line order in the
 *     request does not affect the fingerprint.
 */
public final class OrderFingerprint {

    private OrderFingerprint() {
    }

    public static String of(OrderRequest request) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(request.getShippingAddress() == null ? "" : request.getShippingAddress());

        List<OrderItemRequest> items = request.getItems() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getItems());
        items.sort(Comparator
                .comparing(OrderItemRequest::getProductId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(OrderItemRequest::getQuantity, Comparator.nullsFirst(Comparator.naturalOrder())));

        canonical.append('\n');
        for (OrderItemRequest item : items) {
            canonical.append(item.getProductId()).append(':').append(item.getQuantity()).append(';');
        }
        return sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JLS/Java platform; unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
