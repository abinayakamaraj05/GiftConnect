package com.giftconnect.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The configurable delivery time windows a customer may request for a scheduled
 * gift. These are PREFERRED windows, not exact-minute guarantees, and they are
 * kept in ONE place so the storefront, the API, and the server-side validator
 * never disagree.
 *
 * The {@link #code} is the stable value stored on the order (and sent by the
 * client); the {@link #label} is the human-readable text shown in the picker.
 */
public enum DeliverySlot {

    MORNING("09:00-12:00", "9:00 AM - 12:00 PM"),
    AFTERNOON("12:00-15:00", "12:00 PM - 3:00 PM"),
    EVENING("15:00-18:00", "3:00 PM - 6:00 PM");

    private final String code;
    private final String label;

    DeliverySlot(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Resolves a client-supplied slot by its stable code (e.g. "09:00-12:00") or,
     * leniently, by its enum name (e.g. "MORNING"). Returns empty for anything
     * else so the caller can reject it as an invalid request.
     */
    public static Optional<DeliverySlot> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(slot -> slot.code.equalsIgnoreCase(trimmed) || slot.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }
}
