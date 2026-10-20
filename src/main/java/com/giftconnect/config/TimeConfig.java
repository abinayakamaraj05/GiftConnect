package com.giftconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides a single shared {@link Clock} so all delivery-date decisions are made
 * against ONE injectable, testable source of time instead of scattered
 * {@code LocalDate.now()} / {@code LocalDateTime.now()} calls.
 *
 * The zone is fixed to the delivery location's local zone (Asia/Kolkata for the
 * India-focused experience), so the mandatory seven-calendar-day advance rule is
 * evaluated in the customer's local calendar, not UTC or the server default.
 * Tests inject a fixed Clock to make the rule deterministic.
 */
@Configuration
public class TimeConfig {

    /** The delivery location's local zone. Centralized so it can change in one place. */
    public static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(DELIVERY_ZONE);
    }
}
