package com.giftconnect.filter;

import com.giftconnect.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression tests for {@link AuthFilter}'s public-path matching.
 *
 * The filter used to compare the RAW request URI against allowlist prefixes, so
 * a path such as {@code /api/products/../api/admin/overview} satisfied the
 * {@code /api/products/} prefix and was treated as public even though the
 * container routes it elsewhere. The filter is now driven directly (no routing
 * involved) so the assertion is about the authorization decision itself, not an
 * accident of how Spring happens to route a given URI.
 */
class AuthFilterPathMatchingTest {

    private final UserService userService = mock(UserService.class);
    private final AuthFilter filter = new AuthFilter(userService);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    // ------------------------------------------------------------------
    // Legitimate public routes still work (no session required)
    // ------------------------------------------------------------------

    @Test
    void publicCatalogGet_isReachableWithoutASession() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void publicCatalogSubPathGet_isReachableWithoutASession() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products/5");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ------------------------------------------------------------------
    // Dot-segment traversal must not bypass the filter
    // ------------------------------------------------------------------

    @Test
    void dotSegmentCannotDisguiseAProtectedRouteAsAPublicOne() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products/../api/admin/overview");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void adminPrefixTraversalCannotBypassTheFilter() throws Exception {
        MockHttpServletRequest request = request("GET", "/admin/../api/admin/overview");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void percentEncodedDotCannotBypassTheFilter() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products/%2e%2e/api/admin/overview");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void percentEncodedSlashCannotBypassTheFilter() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products/%2f..%2fapi/admin/overview");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    // ------------------------------------------------------------------
    // Protected routes and method scoping are unchanged
    // ------------------------------------------------------------------

    @Test
    void protectedRouteWithoutASession_is401() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/admin/overview");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void aPublicGetPathIsStillProtectedForPost() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/products");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void aPathThatMerelySharesAPrefixIsNotPublic() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/productsxyz");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void userLookupRemainsProtected() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Authentication required"));
    }
}
