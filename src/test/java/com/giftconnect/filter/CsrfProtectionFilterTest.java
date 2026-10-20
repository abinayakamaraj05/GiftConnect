package com.giftconnect.filter;

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
 * Unit tests for {@link CsrfProtectionFilter}. The filter is driven directly with
 * mock requests/responses so Origin/Referer/Host can be set precisely without a
 * servlet container. A Mockito {@link FilterChain} records whether the request
 * was allowed through.
 */
class CsrfProtectionFilterTest {

    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final CsrfProtectionFilter enabledFilter = new CsrfProtectionFilter(true, "");

    /** Default request: http://localhost (port 80), the origin this server "is". */
    private MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/api/orders");
    }

    @Test
    void safeMethod_isNeverBlockedEvenWithAForeignOrigin() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.addHeader("Origin", "http://evil.example");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void sameOriginPost_isAllowed() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "http://localhost");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void defaultPortAndSchemeVariants_normalizeToTheSameOrigin() throws Exception {
        // Host says the non-default port; the Origin spells out the default one.
        MockHttpServletRequest request = request("PUT");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", "http://localhost:8080");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void crossOriginPost_isRejectedWith403() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "http://evil.example");

        enabledFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Cross-site request blocked"));
    }

    @Test
    void opaqueNullOrigin_isRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "null");

        enabledFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    @Test
    void missingOriginAndReferer_isAllowed() throws Exception {
        MockHttpServletRequest request = request("POST");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void matchingReferer_isAllowedWhenOriginIsAbsent() throws Exception {
        MockHttpServletRequest request = request("DELETE");
        request.addHeader("Referer", "http://localhost/cart.html");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void foreignReferer_isRejectedWhenOriginIsAbsent() throws Exception {
        MockHttpServletRequest request = request("DELETE");
        request.addHeader("Referer", "http://evil.example/page.html");

        enabledFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(403, response.getStatus());
    }

    @Test
    void forwardedProto_isHonoredBehindATlsProxy() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Host", "app.example.com");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Origin", "https://app.example.com");

        enabledFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void configuredAllowListedOrigin_isAccepted() throws Exception {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(true, "https://app.example.com");
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "https://app.example.com");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledFilter_allowsForeignOrigin() throws Exception {
        CsrfProtectionFilter disabled = new CsrfProtectionFilter(false, "");
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "http://evil.example");

        disabled.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
