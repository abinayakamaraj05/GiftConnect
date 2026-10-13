package com.giftconnect.util;

import com.giftconnect.controller.AuthController;
import com.giftconnect.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * The identity behind an audited action, resolved ONLY from the server-side
 * HTTP session that AuthController populated at login.
 *
 * Why this exists: audit rows must record WHO did something, and the only
 * trustworthy source for that is the session. Nothing here reads a user id,
 * email, or role from a request parameter, body, header, or cookie — so a
 * client can never forge an actor or promote itself by sending its own role.
 *
 * Used by ActivityLogService, which keeps the resolution logic in ONE place
 * instead of scattering instanceof checks across every caller.
 */
public final class SessionActor {

    private SessionActor() {
        // static utility — no instances
    }

    /**
     * Immutable snapshot of the acting identity. userId is null when the
     * request has no authenticated session (e.g. a failed login attempt).
     */
    public record Actor(Long userId, Role role, String email) {

        public static Actor anonymous() {
            return new Actor(null, null, null);
        }
    }

    /**
     * Reads the current actor from the session. A missing session (or an id
     * that is not a number) yields {@link Actor#anonymous()} instead of
     * throwing, because audit logging must never break a request.
     *
     * The CUSTOMER fallback for the role mirrors AuthFilter.resolveRole():
     * the least privileged role, so a session without a usable role can never
     * look MORE privileged in the audit trail than it really is.
     */
    public static Actor from(HttpServletRequest request) {
        if (request == null) {
            return Actor.anonymous();
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Actor.anonymous();
        }

        Object userId = session.getAttribute(AuthController.SESSION_USER_ID);
        if (!(userId instanceof Number id)) {
            return Actor.anonymous();
        }

        Object role = session.getAttribute(AuthController.SESSION_USER_ROLE);
        Object email = session.getAttribute(AuthController.SESSION_USER_EMAIL);

        return new Actor(
                id.longValue(),
                role instanceof Role r ? r : Role.CUSTOMER,
                email instanceof String s ? s : null);
    }
}
