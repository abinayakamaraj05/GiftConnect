package com.giftconnect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CSRF defense for this app's cookie-based session auth, layered on top of the
 * {@code SameSite=Lax} session cookie.
 *
 * <p>Runs before {@link AuthFilter}. For any <em>unsafe</em> HTTP method
 * (POST/PUT/PATCH/DELETE) it checks the {@code Origin} header — falling back to
 * {@code Referer} — against the request's own origin (from the {@code Host}
 * header, honoring {@code X-Forwarded-Proto} behind a TLS-terminating proxy).
 * A browser cannot forge {@code Origin}: an attacker page always carries its own
 * origin, so a cross-site submission is rejected with 403 before it can act on
 * the victim's session. Safe methods (GET/HEAD/OPTIONS/TRACE) are never blocked.
 *
 * <p><b>Requests with neither {@code Origin} nor {@code Referer} are allowed.</b>
 * Browsers always send {@code Origin} on cross-site unsafe requests, so this does
 * not open a browser-based CSRF hole, but it means a non-browser client (curl,
 * Postman, server-to-server) is unaffected. This is intentional — it keeps the
 * existing frontend and tooling working without a token handshake — and it is a
 * defense-in-depth layer, with {@code SameSite=Lax} remaining the primary control.
 * An explicit allow-list can be supplied via {@code app.csrf.allowed-origins}.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code app.csrf.enabled} (default true)</li>
 *   <li>{@code app.csrf.allowed-origins} — comma-separated extra origins to trust</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final boolean enabled;
    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CsrfProtectionFilter(
            @Value("${app.csrf.enabled:true}") boolean enabled,
            @Value("${app.csrf.allowed-origins:}") String allowedOrigins) {
        this.enabled = enabled;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CsrfProtectionFilter::normalizeOrigin)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        if (!enabled || method == null || SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            filterChain.doFilter(request, response);
            return;
        }

        String presented = readPresentedOrigin(request);
        if (presented == null) {
            // No Origin and no Referer: not a cross-site browser request — allow.
            filterChain.doFilter(request, response);
            return;
        }

        String normalized = normalizeOrigin(presented);
        boolean trusted = normalized != null
                && (normalized.equals(normalizeOrigin(requestOrigin(request)))
                    || allowedOrigins.contains(normalized));

        if (trusted) {
            filterChain.doFilter(request, response);
            return;
        }

        reject(response);
    }

    /** The caller's claimed origin: Origin header first, then Referer's origin. */
    private String readPresentedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin.trim();
        }

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            String value = referer.trim();
            try {
                URI uri = new URI(value);
                if (uri.getScheme() != null && uri.getAuthority() != null) {
                    return uri.getScheme() + "://" + uri.getAuthority();
                }
            } catch (URISyntaxException ignored) {
                // Fall through and let normalization reject the malformed value.
            }
            return value;
        }

        return null;
    }

    /** The origin this request was actually addressed to (Host + forwarded scheme). */
    private String requestOrigin(HttpServletRequest request) {
        String scheme = firstToken(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        return scheme + "://" + host;
    }

    private static String firstToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * Reduces an origin to a canonical {@code scheme://host:port} so that default
     * ports and casing compare consistently. Returns {@code null} for values that
     * cannot be a concrete trusted origin (missing scheme/host, or the opaque
     * {@code "null"} origin a sandboxed/opaque context sends).
     */
    static String normalizeOrigin(String origin) {
        if (origin == null) {
            return null;
        }
        String value = origin.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", "Cross-site request blocked")));
    }
}
