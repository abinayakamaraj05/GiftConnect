package com.giftconnect.util;

import com.giftconnect.dto.OrderItemRequest;
import com.giftconnect.dto.OrderRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests pinning the canonical form of {@link OrderFingerprint} and, above
 * all, its backward compatibility: a request that uses none of the scheduling /
 * gift fields must hash exactly as it did before those fields existed, so
 * idempotency records written by an older build still match their retries.
 */
class OrderFingerprintTest {

    private static OrderRequest plainRequest(String shippingAddress) {
        OrderRequest request = new OrderRequest();
        request.setShippingAddress(shippingAddress);
        request.setItems(List.of(item(10L, 1)));
        return request;
    }

    private static OrderItemRequest item(Long productId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    /**
     * The pre-scheduling canonicalization was exactly
     * {@code shippingAddress + "\n" + "<productId>:<quantity>;"...}. Reproducing
     * it here makes the compatibility contract explicit: if this test ever
     * breaks, already-stored fingerprints no longer match their retries.
     */
    @Test
    void plainRequest_matchesLegacyCanonicalFormat() {
        OrderRequest request = plainRequest("addr");

        String legacyCanonical = "addr" + "\n" + "10:1;";

        assertEquals(sha256Hex(legacyCanonical), OrderFingerprint.of(request));
    }

    @Test
    void plainRequest_blankSchedulingAndGiftFieldsKeepLegacyFormat() {
        OrderRequest request = plainRequest("addr");
        request.setDeliverySlot("");
        request.setRecipientName("   ");
        request.setGiftMessage("");

        String legacyCanonical = "addr" + "\n" + "10:1;";

        assertEquals(sha256Hex(legacyCanonical), OrderFingerprint.of(request));
    }

    @Test
    void addingSchedule_changesFingerprint() {
        OrderRequest plain = plainRequest("addr");

        OrderRequest scheduled = plainRequest("addr");
        scheduled.setDeliveryDate(LocalDate.parse("2026-10-08"));
        scheduled.setDeliverySlot("09:00-12:00");

        assertNotEquals(OrderFingerprint.of(plain), OrderFingerprint.of(scheduled));
    }

    @Test
    void recipientOrGiftAlone_changesFingerprint() {
        OrderRequest plain = plainRequest("addr");

        OrderRequest withRecipient = plainRequest("addr");
        withRecipient.setRecipientName("Ravi");

        OrderRequest withMessage = plainRequest("addr");
        withMessage.setGiftMessage("Happy Birthday");

        assertNotEquals(OrderFingerprint.of(plain), OrderFingerprint.of(withRecipient));
        assertNotEquals(OrderFingerprint.of(plain), OrderFingerprint.of(withMessage));
    }

    @Test
    void differentGiftMessage_isDetected() {
        OrderRequest first = plainRequest("addr");
        first.setDeliveryDate(LocalDate.parse("2026-10-08"));
        first.setDeliverySlot("09:00-12:00");
        first.setGiftMessage("Happy Birthday");

        OrderRequest second = plainRequest("addr");
        second.setDeliveryDate(LocalDate.parse("2026-10-08"));
        second.setDeliverySlot("09:00-12:00");
        second.setGiftMessage("Happy Anniversary");

        assertNotEquals(OrderFingerprint.of(first), OrderFingerprint.of(second));
    }

    @Test
    void identicalScheduledRequest_isStable() {
        OrderRequest first = plainRequest("addr");
        first.setDeliveryDate(LocalDate.parse("2026-10-08"));
        first.setDeliverySlot("09:00-12:00");
        first.setRecipientName("Ravi");
        first.setGiftMessage("Happy Birthday");

        OrderRequest second = plainRequest("addr");
        second.setDeliveryDate(LocalDate.parse("2026-10-08"));
        second.setDeliverySlot("09:00-12:00");
        second.setRecipientName("Ravi");
        second.setGiftMessage("Happy Birthday");

        assertEquals(OrderFingerprint.of(first), OrderFingerprint.of(second));
    }

    @Test
    void differentDeliveryDate_isDetected() {
        OrderRequest first = plainRequest("addr");
        first.setDeliveryDate(LocalDate.parse("2026-10-08"));
        first.setDeliverySlot("09:00-12:00");

        OrderRequest second = plainRequest("addr");
        second.setDeliveryDate(LocalDate.parse("2026-10-09"));
        second.setDeliverySlot("09:00-12:00");

        assertNotEquals(OrderFingerprint.of(first), OrderFingerprint.of(second));
    }

    @Test
    void itemOrder_doesNotChangeFingerprint() {
        OrderRequest first = new OrderRequest();
        first.setShippingAddress("addr");
        first.setItems(List.of(item(20L, 2), item(10L, 1)));

        OrderRequest second = new OrderRequest();
        second.setShippingAddress("addr");
        second.setItems(List.of(item(10L, 1), item(20L, 2)));

        assertEquals(OrderFingerprint.of(first), OrderFingerprint.of(second));
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
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
