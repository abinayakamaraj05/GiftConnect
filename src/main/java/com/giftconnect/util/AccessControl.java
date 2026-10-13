package com.giftconnect.util;

import com.giftconnect.entity.Role;
import com.giftconnect.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Central role/ownership authorization helpers.
 *
 * The identity used here comes ONLY from the server-side HTTP session that
 * AuthController populated at login (via {@link SessionActor}, which reads the
 * session and nothing else). A caller can never influence its own role or user
 * id through a request parameter, body, header, or cookie.
 *
 * Interactive checks throw {@link AccessDeniedException}, which
 * GlobalExceptionHandler maps to HTTP 403 — so a controller can simply call
 * requireAdmin(...) and let the exception do the rest.
 *
 * Naming mirrors AuthFilter/UserController so the intent of each guard is
 * obvious at the call site.
 */
public final class AccessControl {

    private AccessControl() {
        // static utility — no instances
    }

    /** The authenticated caller's user id, or null when there is no session. */
    public static Long actorId(HttpServletRequest request) {
        return SessionActor.from(request).userId();
    }

    /** The authenticated caller's role, or null for an anonymous request. */
    public static Role actorRole(HttpServletRequest request) {
        return SessionActor.from(request).role();
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return actorRole(request) == Role.ADMIN;
    }

    public static boolean isSeller(HttpServletRequest request) {
        return actorRole(request) == Role.SELLER;
    }

    /**
     * True when the caller is an ADMIN or is the owner of the resource
     * identified by {@code ownerId}. A null caller/owner never matches.
     */
    public static boolean isOwnerOrAdmin(Long ownerId, HttpServletRequest request) {
        if (isAdmin(request)) {
            return true;
        }
        Long actor = actorId(request);
        return actor != null && actor.equals(ownerId);
    }

    /** True when the caller is a SELLER or an ADMIN (i.e. may manage products). */
    public static boolean isSellerOrAdmin(HttpServletRequest request) {
        Role role = actorRole(request);
        return role == Role.ADMIN || role == Role.SELLER;
    }

    /** Throws 403 unless the caller is an ADMIN. */
    public static void requireAdmin(HttpServletRequest request) {
        if (!isAdmin(request)) {
            throw new AccessDeniedException("Only an ADMIN can perform this action");
        }
    }

    /** Throws 403 unless the caller is a SELLER or an ADMIN. */
    public static void requireSellerOrAdmin(HttpServletRequest request) {
        if (!isSellerOrAdmin(request)) {
            throw new AccessDeniedException("Only a SELLER or ADMIN can perform this action");
        }
    }

    /** Throws 403 unless the caller is an ADMIN or the resource owner. */
    public static void requireOwnerOrAdmin(Long ownerId, HttpServletRequest request) {
        if (!isOwnerOrAdmin(ownerId, request)) {
            throw new AccessDeniedException("You do not have access to this resource");
        }
    }

    /** The authenticated caller's id, required to be present. */
    public static Long requireActorId(HttpServletRequest request) {
        Long actor = actorId(request);
        if (actor == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return actor;
    }
}
