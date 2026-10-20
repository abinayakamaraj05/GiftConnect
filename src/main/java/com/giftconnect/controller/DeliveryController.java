package com.giftconnect.controller;

import com.giftconnect.entity.DeliverySlot;
import com.giftconnect.service.DeliveryPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only delivery-scheduling metadata for the checkout date/slot picker.
 *
 * GET /api/delivery/options -> today, the delivery time zone, the minimum advance
 *                              notice, the earliest eligible date, and the slots.
 *
 * Protected by AuthFilter (not in its allowlist), because scheduling happens
 * during an authenticated checkout. The earliest eligible date returned here is
 * a CONVENIENCE for the picker only — the authoritative rule is re-checked
 * server-side in OrderService on every submission, so a client that ignores this
 * endpoint (or forges a date) is still rejected.
 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryPolicy deliveryPolicy;

    @Autowired
    public DeliveryController(DeliveryPolicy deliveryPolicy) {
        this.deliveryPolicy = deliveryPolicy;
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getDeliveryOptions() {
        List<Map<String, String>> slots = Arrays.stream(DeliverySlot.values())
                .map(slot -> Map.of("code", slot.getCode(), "label", slot.getLabel()))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("today", deliveryPolicy.today().toString());
        body.put("timezone", deliveryPolicy.timeZoneId());
        body.put("minAdvanceCalendarDays", DeliveryPolicy.MIN_ADVANCE_CALENDAR_DAYS);
        body.put("earliestEligibleDate", deliveryPolicy.earliestEligibleDate().toString());
        body.put("slots", slots);
        return ResponseEntity.ok(body);
    }
}
