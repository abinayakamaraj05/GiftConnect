package com.giftconnect.service;

import com.giftconnect.config.TimeConfig;
import com.giftconnect.entity.DeliverySlot;
import com.giftconnect.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the seven-calendar-day advance-order rule. A fixed Clock pins
 * "today" so every boundary is deterministic and no wall-clock time is involved.
 */
class DeliveryPolicyTest {

    private static final ZoneId ZONE = TimeConfig.DELIVERY_ZONE;

    /** A policy whose clock is pinned to {@code localDate} at the given local hour:minute. */
    private DeliveryPolicy policyAt(String localDate, int hour, int minute) {
        Instant instant = LocalDate.parse(localDate).atTime(hour, minute).atZone(ZONE).toInstant();
        return new DeliveryPolicy(Clock.fixed(instant, ZONE));
    }

    private DeliveryPolicy policyAtNoon(String localDate) {
        return policyAt(localDate, 12, 0);
    }

    @Test
    void earliestEligible_isExactlySevenCalendarDaysAhead() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertEquals(LocalDate.parse("2026-10-01"), policy.today());
        assertEquals(LocalDate.parse("2026-10-08"), policy.earliestEligibleDate());
        assertEquals("Asia/Kolkata", policy.timeZoneId());
    }

    @Test
    void sevenDaysAhead_isAccepted() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        DeliverySlot slot = policy.validateAndResolveSlot(LocalDate.parse("2026-10-08"), "09:00-12:00");
        assertEquals(DeliverySlot.MORNING, slot);
    }

    @Test
    void sixDaysAhead_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-10-07"), "09:00-12:00"));
        assertTrue(ex.getMessage().contains("at least 7"), ex.getMessage());
    }

    @Test
    void sameDayDelivery_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-10-01"), "09:00-12:00"));
    }

    @Test
    void pastDate_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-09-20"), "09:00-12:00"));
    }

    @Test
    void monthBoundary_crossesCorrectly() {
        DeliveryPolicy policy = policyAtNoon("2026-10-26");
        assertEquals(LocalDate.parse("2026-11-02"), policy.earliestEligibleDate());
        assertDoesNotThrow(() -> policy.validateAndResolveSlot(LocalDate.parse("2026-11-02"), "12:00-15:00"));
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-11-01"), "12:00-15:00"));
    }

    @Test
    void yearBoundary_crossesCorrectly() {
        DeliveryPolicy policy = policyAtNoon("2026-12-28");
        assertEquals(LocalDate.parse("2027-01-04"), policy.earliestEligibleDate());
        assertDoesNotThrow(() -> policy.validateAndResolveSlot(LocalDate.parse("2027-01-04"), "15:00-18:00"));
    }

    @Test
    void leapYear_februaryBoundary_crossesCorrectly() {
        // 2024 is a leap year: 23 Feb + 7 calendar days = 1 Mar.
        DeliveryPolicy policy = policyAtNoon("2024-02-23");
        assertEquals(LocalDate.parse("2024-03-01"), policy.earliestEligibleDate());
        assertDoesNotThrow(() -> policy.validateAndResolveSlot(LocalDate.parse("2024-03-01"), "09:00-12:00"));
        // 29 Feb is only 6 days after 23 Feb, so it must be rejected.
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2024-02-29"), "09:00-12:00"));
    }

    @Test
    void ruleIsCalendarDayBased_notRollingHours() {
        // Ordered late at night on 1 Oct: a rolling 168-hour rule would push the
        // earliest date past 8 Oct, but the calendar-day rule still allows 8 Oct.
        DeliveryPolicy policy = policyAt("2026-10-01", 23, 59);
        assertEquals(LocalDate.parse("2026-10-08"), policy.earliestEligibleDate());
        assertDoesNotThrow(() -> policy.validateAndResolveSlot(LocalDate.parse("2026-10-08"), "09:00-12:00"));
    }

    @Test
    void invalidSlot_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-10-08"), "MIDNIGHT"));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid delivery slot"), ex.getMessage());
    }

    @Test
    void blankSlot_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(LocalDate.parse("2026-10-08"), "   "));
    }

    @Test
    void missingDate_isRejected() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertThrows(InvalidOperationException.class,
                () -> policy.validateAndResolveSlot(null, "09:00-12:00"));
    }

    @Test
    void slotAcceptedByEnumNameAsWellAsCode() {
        DeliveryPolicy policy = policyAtNoon("2026-10-01");
        assertEquals(DeliverySlot.AFTERNOON,
                policy.validateAndResolveSlot(LocalDate.parse("2026-10-08"), "AFTERNOON"));
    }
}
