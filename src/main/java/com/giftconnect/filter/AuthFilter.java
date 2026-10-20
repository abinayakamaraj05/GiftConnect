package com.giftconnect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftconnect.controller.AuthController;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs once per request and enforces session-based authentication.
 *
 * PUBLIC_PATHS bypass the check entirely (landing page, static assets,
 * and the auth endpoints themselves — you can't require a session to log in).
 *
 * Everything else requires a valid session. GET /api/users and GET /api/users/{id}
 * used to be public during Week 1 and were removed from this allowlist because they
 * expose user data, so user lookup now needs a logged-in session. To protect a new
 * endpoint later, simply don't add its path to PUBLIC_PATHS — the filter will
 * require a valid session automatically.
 *
 * For every authenticated request the filter ALSO re-validates the session
 * against the authoritative database row (Phase 4Q): it refreshes the session's
 * role/email from the CURRENT user record and destroys the session the moment
 * the account is INACTIVE or no longer exists. This means an ADMIN's role or
 * status change takes effect on the user's very next request instead of lingering
 * until logout, and a deactivated account cannot keep using protected endpoints
 * through an old session. The refreshed role is exposed as the REQUEST_USER_ROLE
 * request attribute so downstream controllers/services can read it without
 * touching the session themselves. The role is never read from a request
 * parameter, body, header, or cookie. No role-based rules are enforced here —
 * authentication plus freshness; CUSTOMER/SELLER/ADMIN authorization comes later.
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
            // Phase 1 (email OTP registration): these two MUST be public because
            // the caller has no session yet — they only trigger sending a code to
            // an email address and comparing it to a stored hash; they never
            // return user data. Removing them would make registration impossible.
            "/api/auth/verify-otp",
            "/api/auth/resend-otp",
            // Phase 1 (new static pages): HTML/JS assets must load BEFORE any
            // session exists. The prefix list above only covers /css/ and /js/.
            "/verify.html",
            "/profile.html",
            "/admin/",
            "/seller/"
            // NOTE: "/api/users" and "/api/users/{id}" were deliberately removed —
            // both now require an authenticated session because they expose user data.
    );

    /**
     * Phase 2 — public catalog browsing. These paths are readable WITHOUT a session,
     * but ONLY for GET requests: the same paths still require authentication for
     * POST/PUT/DELETE, so anonymous visitors can browse while all mutations remain
     * locked down (and ADMIN-only in the controllers). The service layer hides
     * non-ACTIVE products from these anonymous reads.
     */
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/products",
            "/api/products/",
            "/api/categories",
            "/api/categories/"
    );

    /**
     * Request attribute holding the authenticated caller's role, resolved from the
     * HTTP session. Downstream controllers/services can read it via
     * request.getAttribute(AuthFilter.REQUEST_USER_ROLE) instead of touching the
     * session directly.
     */
    public static final String REQUEST_USER_ROLE = "authenticatedRole";

    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AuthFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        boolean isAuthenticated = session != null && session.getAttribute(AuthController.SESSION_USER_ID) != null;

        if (!isAuthenticated) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        // Phase 4Q — never trust the role/status snapshot stored in the session at
        // login. Re-check the authoritative database row and refresh the session
        // from it so ADMIN role/status changes apply on the very next request and
        // a deactivated/deleted account is stopped immediately. The userId still
        // comes ONLY from the server-side session — a client can never influence
        // identity or privilege through a parameter, body, header, or cookie.
        Role role = resolveAuthoritativeRole(response, session);
        if (role == null) {
            return; // the rejection was already written
        }

        // Expose the now-fresh role to downstream controllers/services.
        request.setAttribute(REQUEST_USER_ROLE, role);

        filterChain.doFilter(request, response);
    }

    /**
     * Re-validates the session's user against the database and refreshes the
     * session's role/email from the current record, so every downstream read
     * (AccessControl, SessionActor, /api/auth/me) sees the CURRENT state.
     *
     * Returns the role to expose on the request. Returns null when the request
     * must be rejected instead — in that case a 401/403 has already been written
     * and the session has been invalidated (a deactivated or deleted identity is
     * gone, not just paused).
     */
    private Role resolveAuthoritativeRole(HttpServletResponse response,
                                          HttpSession session) throws IOException {
        Object idAttr = session.getAttribute(AuthController.SESSION_USER_ID);
        if (!(idAttr instanceof Number id)) {
            session.invalidate();
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return null;
        }

        Optional<User> dbUser = userService.getUserByIdOptional(id.longValue());
        if (dbUser.isEmpty()) {
            // The account no longer exists outside the app (the app never deletes
            // users). Its session is dead — invalidate it so the stale identity
            // cannot be resurrected by a future account with the same id.
            session.invalidate();
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return null;
        }

        User user = dbUser.get();

        // Phase 5 status change while logged in: stop the account at once and
        // destroy its session. An admin who later reactivates the account must
        // log in again (see AdminService — nothing is deleted, so the account,
        // orders, and payments are exactly as they were).
        if (!user.isActive()) {
            session.invalidate();
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "This account has been deactivated. Please contact support.");
            return null;
        }

        // Authoritative refresh: re-stamp what login originally stored so the
        // role (and email snapshot) is current — an ADMIN promotion/demotion
        // takes effect on this very request, with no re-login required.
        session.setAttribute(AuthController.SESSION_USER_ROLE, user.getRole());
        session.setAttribute(AuthController.SESSION_USER_EMAIL, user.getEmail());
        return user.getRole();
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = publicMatchPath(request);
        if (path == null) {
            // Unresolvable or deliberately non-normalizable path — fail closed.
            return false;
        }

        if (matchesAny(PUBLIC_PATHS, path)) {
            return true;
        }

        // Phase 2 — catalog reads are public for GET only. A POST/PUT/DELETE on
        // the same paths never reaches this branch, so anonymous writes are still
        // rejected here with 401 before any controller runs.
        return isGet(request) && matchesAny(PUBLIC_GET_PATHS, path);
    }

    /**
     * Returns the request path in a form safe to match allowlist prefixes against,
     * or {@code null} when the path must not be treated as public.
     *
     * The raw {@link HttpServletRequest#getRequestURI()} is used verbatim by the
     * container for dispatch, so a path such as {@code /api/products/../api/users}
     * satisfies the {@code /api/products/} prefix here yet is routed (and would be
     * authorized) differently downstream. To close that gap the path is resolved
     * with {@link StringUtils#cleanPath} first, so dot-segments cannot smuggle a
     * protected route past the prefix check.
     *
     * Percent-encoded separators/dots ({@code %2e}, {@code %2f}, {@code %5c},
     * {@code %00}) are treated as non-public: Tomcat may decode them before
     * routing while this filter sees the encoded form, so matching them against a
     * public prefix could disagree with the final dispatch target. No legitimate
     * public page or API path in this app uses those encodings, so failing closed
     * costs nothing and removes the ambiguity.
     */
    private String publicMatchPath(HttpServletRequest request) {
        String raw = request.getRequestURI();
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%2f")
                || lower.contains("%5c") || lower.contains("%00")) {
            return null;
        }

        String cleaned = org.springframework.util.StringUtils.cleanPath(raw);
        return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }

    private boolean matchesAny(List<String> allowed, String path) {
        return allowed.stream().anyMatch(candidate -> {
            // Exact match always counts (this is the only way "/" can match).
            if (path.equals(candidate)) {
                return true;
            }
            // Prefix entries are ONLY the ones written with a trailing slash
            // (e.g. "/css/", "/js/", "/api/products/"). Without this guard, the
            // "/" entry would prefix-match every path in the app and make nothing
            // protected.
            return candidate.endsWith("/") && !candidate.equals("/") && path.startsWith(candidate);
        });
    }

    private boolean isGet(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod());
    }
}
