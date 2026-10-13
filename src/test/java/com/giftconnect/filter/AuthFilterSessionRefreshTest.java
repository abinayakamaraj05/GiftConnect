package com.giftconnect.filter;

import com.giftconnect.controller.AdminController;
import com.giftconnect.controller.AuthController;
import com.giftconnect.controller.ProductController;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.dto.SystemOverviewResponse;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.Role;
import com.giftconnect.service.AdminService;
import com.giftconnect.service.ProductService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4Q — AuthFilter must re-validate a session's role and account status
 * against the authoritative database on EVERY protected request and refresh the
 * session from it, so ADMIN role/status changes take effect on the very next
 * request instead of lingering until logout, and a deactivated account cannot
 * keep using protected endpoints through an old session.
 *
 * Each test builds a session exactly as POST /api/auth/login stores one (the
 * SESSION snapshot is therefore allowed to already be stale), then makes
 * UserService return the CURRENT database row — the same trust boundary as
 * production: the client cannot influence the value. No database or SMTP is
 * touched; every collaborating service is a mock.
 *
 * Endpoints used as probes:
 *   GET  /api/admin/overview  -> ADMIN-only (AdminController reads the request
 *                                attribute AuthFilter sets from the session)
 *   POST /api/products        -> SELLER-or-ADMIN (AccessControl reads the session)
 */
@WebMvcTest({AdminController.class, ProductController.class})
class AuthFilterSessionRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private UserService userService;

    private static final Long USER_ID = 7L;
    private static final String PRODUCT_BODY =
            "{\"productName\":\"Mug\",\"price\":10.0,\"stock\":5,\"categoryId\":1}";

    /** What the database row currently says — the authoritative truth. */
    private void dbUser(Long id, Role role, AccountStatus status) {
        when(userService.getUserByIdOptional(id))
                .thenReturn(Optional.of(TestSessions.user(id, role, status)));
    }

    private void dbUser(Long id, Role role) {
        dbUser(id, role, AccountStatus.ACTIVE);
    }

    /** Builds a session the way login would on the (possibly stale) DAY of login. */
    private MockHttpSession sessionSnapshot(Long userId, Role roleAtLogin) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, userId);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, roleAtLogin);
        return session;
    }

    private Role sessionRole(MockHttpSession session) {
        return (Role) session.getAttribute(AuthController.SESSION_USER_ROLE);
    }

    // ------------------------------------------------------------------
    // Promotion
    // ------------------------------------------------------------------

    @Test
    void promotedToAdmin_canUseAdminEndpointsImmediately_withoutRelogin() throws Exception {
        // Logged in as CUSTOMER; an admin then promoted the account to ADMIN.
        dbUser(USER_ID, Role.ADMIN);
        when(adminService.getOverview()).thenReturn(new SystemOverviewResponse());
        MockHttpSession session = sessionSnapshot(USER_ID, Role.CUSTOMER);

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isOk());

        // The stale session was refreshed, not a one-off value on the request.
        assertEquals(Role.ADMIN, sessionRole(session));
    }

    @Test
    void promotedToSeller_canCreateProductsImmediately_withoutRelogin() throws Exception {
        dbUser(USER_ID, Role.SELLER);
        when(productService.addProduct(any(), any())).thenReturn(new ProductResponse());
        MockHttpSession session = sessionSnapshot(USER_ID, Role.CUSTOMER);

        mockMvc.perform(post("/api/products").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY))
                .andExpect(status().isCreated());

        assertEquals(Role.SELLER, sessionRole(session));
    }

    // ------------------------------------------------------------------
    // Demotion
    // ------------------------------------------------------------------

    @Test
    void demotedAdmin_isBlockedFromAdminEndpointsImmediately() throws Exception {
        // Logged in as ADMIN; an admin then demoted this account to CUSTOMER.
        dbUser(USER_ID, Role.CUSTOMER);
        MockHttpSession session = sessionSnapshot(USER_ID, Role.ADMIN);

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isForbidden());

        // The session no longer claims ADMIN, so no future request can skate by.
        assertEquals(Role.CUSTOMER, sessionRole(session));
    }

    @Test
    void demotedFromSeller_cannotCreateProducts() throws Exception {
        // Logged in as SELLER; the account was demoted to CUSTOMER.
        dbUser(USER_ID, Role.CUSTOMER);
        MockHttpSession session = sessionSnapshot(USER_ID, Role.SELLER);

        mockMvc.perform(post("/api/products").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY))
                .andExpect(status().isForbidden());

        // The stale elevated session never reached the service.
        verify(productService, never()).addProduct(any(), any());
        assertEquals(Role.CUSTOMER, sessionRole(session));
    }

    // ------------------------------------------------------------------
    // Deactivation
    // ------------------------------------------------------------------

    @Test
    void deactivatedAccount_isRejected_andSessionInvalidated() throws Exception {
        dbUser(USER_ID, Role.ADMIN, AccountStatus.INACTIVE);
        MockHttpSession session = sessionSnapshot(USER_ID, Role.ADMIN);

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("This account has been deactivated. Please contact support."));

        // The session was destroyed — deactivation is immediate, not soft.
        assertTrue(session.isInvalid());
    }

    @Test
    void deactivatedAccount_cannotReuseTheOldSession() throws Exception {
        dbUser(USER_ID, Role.ADMIN, AccountStatus.INACTIVE);
        MockHttpSession session = sessionSnapshot(USER_ID, Role.ADMIN);

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isForbidden());

        // The SAME browser session is now treated as anonymous: 401, not 403.
        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccount_isRejected_andSessionInvalidated() throws Exception {
        when(userService.getUserByIdOptional(USER_ID)).thenReturn(Optional.empty());
        MockHttpSession session = sessionSnapshot(USER_ID, Role.ADMIN);

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    // ------------------------------------------------------------------
    // Reactivation
    // ------------------------------------------------------------------

    @Test
    void reactivatedAccount_regainsAccessAfterLoggingInAgain() throws Exception {
        // Deactivation destroyed the old session (see deactivation tests above).
        dbUser(USER_ID, Role.ADMIN, AccountStatus.INACTIVE);
        MockHttpSession oldSession = sessionSnapshot(USER_ID, Role.ADMIN);
        mockMvc.perform(get("/api/admin/overview").session(oldSession))
                .andExpect(status().isForbidden());
        assertTrue(oldSession.isInvalid());

        // An admin reactivates the account; the user logs in again -> fresh session.
        dbUser(USER_ID, Role.ADMIN, AccountStatus.ACTIVE);
        when(adminService.getOverview()).thenReturn(new SystemOverviewResponse());
        MockHttpSession freshSession = sessionSnapshot(USER_ID, Role.ADMIN);

        mockMvc.perform(get("/api/admin/overview").session(freshSession))
                .andExpect(status().isOk());
        assertEquals(Role.ADMIN, sessionRole(freshSession));
    }

    // ------------------------------------------------------------------
    // Control — unchanged active accounts behave exactly as before
    // ------------------------------------------------------------------

    @Test
    void unchangedActiveCustomer_getsNoNewPrivilege() throws Exception {
        dbUser(USER_ID, Role.CUSTOMER);
        MockHttpSession session = sessionSnapshot(USER_ID, Role.CUSTOMER);

        mockMvc.perform(post("/api/products").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/overview").session(session))
                .andExpect(status().isForbidden());

        assertEquals(Role.CUSTOMER, sessionRole(session));
    }
}