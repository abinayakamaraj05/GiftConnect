package com.giftconnect.service;

import com.giftconnect.entity.DeliverySlot;
import com.giftconnect.exception.InvalidOperationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Owns the signature GiftConnect delivery rule: a scheduled gift must be ordered
 * at least seven CALENDAR days before its requested delivery date.
 *
 *   earliestEligibleDeliveryDate = orderPlacementLocalDate + 7 calendar days
 *
 * "Calendar days" (not a rolling 168-hour window) is deliberate: a gift ordered
 * on the 1st may be delivered on the 8th regardless of the time of day it was
 * ordered. All "today" and "now" values come from the injected {@link Clock},
 * so tests can pin the date and the rule is never evaluated against wall-clock
 * time or a server-default time zone.
 */
@Component
public class DeliveryPolicy {

    /** Minimum advance notice, in calendar days. */
    public static final int MIN_ADVANCE_CALENDAR_DAYS = 7;

    private final Clock clock;

    public DeliveryPolicy(Clock clock) {
        this.clock = clock;
    }

    /** Today's date in the delivery zone. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The first date a gift ordered today may be scheduled for. */
    public LocalDate earliestEligibleDate() {
        return today().plusDays(MIN_ADVANCE_CALENDAR_DAYS);
    }

    /** Current date-time in the delivery zone (used for genuine delivery timestamps). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** The IANA zone id the rule was evaluated in, surfaced to clients and stored on the order. */
    public String timeZoneId() {
        return clock.getZone().getId();
    }

    /**
     * Validates a requested delivery date and slot, returning the resolved slot.
     *
     * Rejects, with a 400 (InvalidOperationException), a missing date, a missing
     * or unknown slot, and any date earlier than the seven-calendar-day minimum.
     * The rule is ALWAYS enforced here on the server, independent of any
     * front-end date-picker restriction.
     */
    public DeliverySlot validateAndResolveSlot(LocalDate requestedDate, String slotValue) {
        if (requestedDate == null) {
            throw new InvalidOperationException(
                    "deliveryDate is required when scheduling a gift");
        }
        if (slotValue == null || slotValue.isBlank()) {
            throw new InvalidOperationException(
                    "deliverySlot is required when scheduling a gift");
        }

        DeliverySlot slot = DeliverySlot.fromCode(slotValue)
                .orElseThrow(() -> new InvalidOperationException(
                        "Invalid delivery slot: '" + slotValue + "'. Allowed codes: " +
                                Arrays.stream(DeliverySlot.values())
                                        .map(DeliverySlot::getCode)
                                        .collect(Collectors.joining(", "))));

        LocalDate earliest = earliestEligibleDate();
        if (requestedDate.isBefore(earliest)) {
            throw new InvalidOperationException(
                    "Delivery date must be at least " + MIN_ADVANCE_CALENDAR_DAYS +
                            " calendar days from today. Earliest available: " + earliest +
                            ", requested: " + requestedDate);
        }
        return slot;
    }
}
