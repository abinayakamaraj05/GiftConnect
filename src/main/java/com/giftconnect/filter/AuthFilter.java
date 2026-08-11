package com.giftconnect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftconnect.controller.AuthController;
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
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/index.html",
            "/css/",
            "/js/",
            "/api/auth/register",
            "/api/auth/login",
            "/api/users" // Day 1 lookup endpoints stay public for now
    );

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

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath ->
                path.equals(publicPath) || path.startsWith(publicPath.endsWith("/") ? publicPath : publicPath + "/")
        );
    }
}
