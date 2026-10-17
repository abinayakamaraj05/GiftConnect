package com.giftconnect.controller;

import com.giftconnect.dto.ChangePasswordRequest;
import com.giftconnect.dto.ProfileUpdateRequest;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.EmailService;
import com.giftconnect.service.OtpService;
import com.giftconnect.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fail-closed guards on the three account-management endpoints, exercised with
 * AuthFilter OUT OF THE PICTURE — controllers are invoked directly with a
 * request that has no session.
 *
 * Why this exists alongside the MockMvc tests (which run through the real
 * filter and also assert 401): AuthFilter decides publicity by matching the RAW
 * request URI against PUBLIC_PATHS, so a path such as
 * "/admin/../api/users/me" satisfies the "/admin/" prefix and skips the filter.
 * Nothing exploitable follows from that today — Spring cannot route a path that
 * still contains "..", so the request dies in the static-resource handler
 * instead of reaching a controller — but that is a routing accident, not an
 * authorization decision. These tests pin the property that must not depend on
 * routing luck: with no session, each endpoint refuses to act, touches no
 * service, and writes no audit row.
 */
class AccountEndpointFailClosedTest {

    private final UserService userService = mock(UserService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final OtpService otpService = mock(OtpService.class);
    private final EmailService emailService = mock(EmailService.class);

    /** A request with no HTTP session at all — i.e. an anonymous caller. */
    private HttpServletRequest anonymousRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        return request;
    }

    // ------------------------------------------------------------------
    // GET /api/users/me
    // ------------------------------------------------------------------

    @Test
    void getCurrentUser_withoutASession_is401_evenWhenTheFilterIsSkipped() {
        UserController controller = new UserController(userService, activityLogService);

        ResponseEntity<?> response = controller.getCurrentUser(anonymousRequest());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(userService, activityLogService);
    }

    // ------------------------------------------------------------------
    // PUT /api/users/me
    // ------------------------------------------------------------------

    @Test
    void updateCurrentUser_withoutASession_is401_andTouchesNothing() {
        UserController controller = new UserController(userService, activityLogService);

        ProfileUpdateRequest body = new ProfileUpdateRequest();
        body.setName("Hijacked");

        ResponseEntity<?> response = controller.updateCurrentUser(body, anonymousRequest());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(userService, activityLogService);
    }

    // ------------------------------------------------------------------
    // POST /api/auth/change-password
    // ------------------------------------------------------------------

    @Test
    void changePassword_withoutASession_is401_andNeverRotatesCredentials() {
        AuthController controller = new AuthController(userService, otpService, emailService, activityLogService);

        ChangePasswordRequest body = new ChangePasswordRequest();
        body.setCurrentPassword("StolenPass1!");
        body.setNewPassword("AttackerPass1!");

        ResponseEntity<?> response = controller.changePassword(body, anonymousRequest());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(userService, activityLogService);
    }
}
