package com.giftconnect.controller;

import com.giftconnect.dto.UserRoleUpdateRequest;
import com.giftconnect.dto.UserStatusUpdateRequest;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.filter.AuthFilter;
import com.giftconnect.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ADMIN-only reporting endpoints.
 *
 *   GET /api/admin/overview  -> platform counts + recent activity/security events
 *   GET /api/admin/activity  -> paged, filterable audit trail
 *
 * Phase 5 — user management (also ADMIN-only):
 *   GET /api/admin/users                 -> paged user list (search/role/status)
 *   GET /api/admin/users/{id}            -> one user's safe profile (404 if absent)
 *   PUT /api/admin/users/{id}/status     -> activate / deactivate an account
 *   PUT /api/admin/users/{id}/role       -> change a user's role
 *
 * Authorization: the caller's role is read from the request attribute that
 * AuthFilter resolved from the SERVER-SIDE session — never from a parameter,
 * body, header, or cookie — and non-ADMIN callers get 403. AuthFilter
 * additionally requires an authenticated session here because "/api/admin" is
 * deliberately NOT in its PUBLIC_PATHS allowlist.
 *
 * Read-only by design: no endpoint anywhere creates, edits, or deletes audit rows.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can view system activity");
        }
        return ResponseEntity.ok(adminService.getOverview());
    }

    /**
     * Optional filters: userId, role, type, entityType, status, from, to.
     * Pagination: page (0-based), size (clamped to 100). An unknown filter value
     * (e.g. type=NOT_A_TYPE) is rejected as 400 by GlobalExceptionHandler rather
     * than silently ignored.
     */
    @GetMapping("/activity")
    public ResponseEntity<?> activity(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) Role role,
                                      @RequestParam(required = false) ActivityType type,
                                      @RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) ActivityStatus status,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can view system activity");
        }
        return ResponseEntity.ok(adminService.getActivityLog(
                userId, role, type, entityType, status, from, to, page, size));
    }

    // ==================================================================
    // Phase 5 — ADMIN user management
    // ==================================================================

    /**
     * Paged user list. Optional filters: search (name OR email), role, status.
     * Pagination: page (0-based), size (clamped to 100 server-side).
     *
     * Responses are built from UserResponse, so password hashes and every OTP
     * field are structurally impossible to return.
     */
    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) Role role,
                                   @RequestParam(required = false) AccountStatus status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can list user accounts");
        }
        return ResponseEntity.ok(adminService.getUserPage(search, role, status, page, size));
    }

    /** One user's safe profile; 404 when the id does not exist. */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> user(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can view user accounts");
        }
        return ResponseEntity.ok(adminService.getUserDetail(id, request));
    }

    /**
     * Activate or deactivate an account. Body: {"status":"ACTIVE"|"INACTIVE"}.
     * An unknown value is a 400. Self-deactivation and deactivating the last
     * active ADMIN are refused with a clear message (see AdminService).
     */
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id,
                                              @Valid @RequestBody UserStatusUpdateRequest body,
                                              HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can change account status");
        }
        return ResponseEntity.ok(adminService.changeStatus(id, body.getStatus(), request));
    }

    /**
     * Change a user's role. Body: {"role":"CUSTOMER"|"SELLER"|"ADMIN"}.
     * An unknown role is a 400. Changing your own role and demoting the last
     * active ADMIN are refused with a clear message (see AdminService).
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @Valid @RequestBody UserRoleUpdateRequest body,
                                            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return forbidden("Only an ADMIN can change user roles");
        }
        return ResponseEntity.ok(adminService.changeRole(id, body.getRole(), request));
    }

    /**
     * Role resolved by AuthFilter from the session. Anything unexpected (missing
     * attribute, different role) is not an admin — the safe default.
     */
    private boolean isAdmin(HttpServletRequest request) {
        return request.getAttribute(AuthFilter.REQUEST_USER_ROLE) == Role.ADMIN;
    }

    private ResponseEntity<Map<String, String>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", message));
    }
}
