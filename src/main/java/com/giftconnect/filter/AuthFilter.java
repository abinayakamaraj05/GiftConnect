package com.giftconnect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftconnect.controller.AuthController;
import com.giftconnect.entity.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runs once per request and enforces session-based authentication.
 *
 * PUBLIC_PATHS bypass the check entirely (landing page, static assets,
 * and the auth endpoints themselves — you can't require a session to log in).
 *
 * Everything else currently used by the app (GET /api/users, /api/users/{id})
 * is also left public for now, since Week 1 only asks us to protect FUTURE
 * authenticated endpoints, not rework existing Day 1 behavior. To protect a
 * new endpoint later, simply don't add its path to PUBLIC_PATHS — the filter
 * will require a valid session automatically.
 *
 * For every authenticated request the filter also resolves the caller's role from
 * the server-side HTTP session and exposes it as the REQUEST_USER_ROLE request
 * attribute, so downstream controllers/services can read it without touching the
 * session themselves. The role is never read from a request parameter, body,
 * header, or cookie. No role-based rules are enforced here — authentication only;
 * CUSTOMER/SELLER/ADMIN authorization comes later.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/index.html",
            "/dashboard.html",
            "/categories.html",
            "/products.html",
            "/css/",
            "/js/",
            "/api/auth/register",
            "/api/auth/login",
            "/api/users",  // exact: GET /api/users
            "/api/users/"  // prefix: GET /api/users/{id}
    );

    /**
     * Request attribute holding the authenticated caller's role, resolved from the
     * HTTP session. Downstream controllers/services can read it via
     * request.getAttribute(AuthFilter.REQUEST_USER_ROLE) instead of touching the
     * session directly.
     */
    public static final String REQUEST_USER_ROLE = "authenticatedRole";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        boolean isAuthenticated = session != null && session.getAttribute(AuthController.SESSION_USER_ID) != null;

        if (!isAuthenticated) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "Authentication required")));
            return;
        }

        // Expose the authenticated caller's role to downstream controllers/services.
        // It is read ONLY from the server-side HTTP session (AuthController stored it
        // at login) — never from a request parameter, body, header, or cookie.
        request.setAttribute(REQUEST_USER_ROLE, resolveRole(session));

        filterChain.doFilter(request, response);
    }

    /**
     * Reads the role AuthController stored in the session at login.
     *
     * A session created before roles existed (or holding anything unexpected) has no
     * usable role, so it safely falls back to Role.CUSTOMER — the same default the
     * User entity uses. No privilege is gained either way: the value only comes from
     * the session, so a client cannot promote itself by sending a role of its own.
     */
    private Role resolveRole(HttpSession session) {
        Object sessionRole = session.getAttribute(AuthController.SESSION_USER_ROLE);
        if (sessionRole instanceof Role role) {
            return role;
        }
        return Role.CUSTOMER;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> {
            // Exact match always counts (this is the only way "/" can match).
            if (path.equals(publicPath)) {
                return true;
            }
            // Prefix entries are ONLY the ones written with a trailing slash
            // (e.g. "/css/", "/js/"). Without this guard, the "/" entry would
            // prefix-match every path in the app and make nothing protected.
            return publicPath.endsWith("/") && !publicPath.equals("/") && path.startsWith(publicPath);
        });
    }
}
