package com.giftconnect.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Safely extracts the caller's IP for audit logging.
 *
 * Behind a proxy/load balancer the socket address is the proxy, so the
 * standard X-Forwarded-For convention is honored — but only its FIRST entry
 * (the original client as added by the trusted edge) is used, and only from
 * the request's own header set. The value is length-capped to fit the DB
 * column (45 = IPv6 max) and never trusted for authorization decisions.
 */
public final class IpUtils {

    private static final int MAX_IP_LENGTH = 45;
    private static final String UNKNOWN = "unknown";

    private IpUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First entry = original client per the X-Forwarded-For convention.
            String candidate = forwarded.split(",")[0].trim();
            return cap(candidate);
        }

        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) return null;
        return cap(remote);
    }

    private static String cap(String value) {
        if (value == null || value.isBlank() || UNKNOWN.equalsIgnoreCase(value)) return null;
        return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
    }
}