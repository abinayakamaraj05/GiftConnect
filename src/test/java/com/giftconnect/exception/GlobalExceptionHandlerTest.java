package com.giftconnect.exception;

import com.giftconnect.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for GlobalExceptionHandler's leak-prevention behavior.
 *
 * Two defects used to exist:
 *   1. Unknown routes / missing static resources were caught by the catch-all
 *      handler and turned into a 500 whose body echoed Spring's internal
 *      "No static resource ..." message.
 *   2. The catch-all handler echoed the raw exception message to the client.
 *
 * Both are now fixed: unknown routes are a flat 404 "Not found", and unexpected
 * exceptions are a generic 500 "Internal server error" with the real cause only
 * logged server-side. The curated handlers (400/403/404/409) are unchanged.
 *
 * Runs in the real MVC slice so the unmapped-route test exercises the actual
 * NoResourceFoundException path (raised by Spring's static-resource handler).
 * Filters are disabled so AuthFilter does not short-circuit the synthetic
 * /test/** request paths; UserService is mocked only because AuthFilter is
 * component-scanned into the slice and needs it at construction time.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestControllerConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/test/boom")
        void boom() {
            throw new RuntimeException("boom-secret");
        }

        @GetMapping("/test/no-resource")
        void noResource() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/no-resource");
        }

        @GetMapping("/test/invalid-operation")
        void invalidOperation() {
            throw new InvalidOperationException("Invalid operation message");
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Resource missing message");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("Access denied message");
        }

        @GetMapping("/test/duplicate")
        void duplicate() {
            throw new DuplicateResourceException("Duplicate message");
        }

        @GetMapping("/test/method-only")
        void methodOnly() {
            // Only GET is mapped here, so any other verb triggers a 405.
        }

        @PostMapping(value = "/test/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void jsonEndpoint(@RequestBody String body) {
            // Consumes JSON only, so a different Content-Type triggers a 415.
        }

        @GetMapping("/test/typed/{id}")
        void typed(@PathVariable Long id) {
            // A non-numeric {id} cannot be bound, triggering a type mismatch.
        }
    }

    @Test
    void unknownRoute_returns404_withoutExposingStaticResourceMessage() throws Exception {
        mockMvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("No static resource"))));
    }

    @Test
    void noResourceFound_returns404_withStableMessage() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(content().string(not(containsString("No static resource"))));
    }

    @Test
    void unexpectedException_returns500_withGenericMessage_noLeak() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(content().string(not(containsString("boom-secret"))))
                .andExpect(content().string(not(containsString("Something went wrong"))));
    }

    @Test
    void invalidOperation_isPreservedAs400_withSafeMessage() throws Exception {
        mockMvc.perform(get("/test/invalid-operation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid operation message"));
    }

    @Test
    void resourceNotFound_isPreservedAs404_withSafeMessage() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource missing message"));
    }

    @Test
    void accessDenied_isPreservedAs403_withSafeMessage() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied message"));
    }

    @Test
    void duplicateResource_isPreservedAs409_withSafeMessage() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate message"));
    }

    @Test
    void wrongHttpMethod_returns405_withSafeMessage() throws Exception {
        mockMvc.perform(post("/test/method-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("HTTP method not supported for this endpoint"));
    }

    @Test
    void unsupportedContentType_returns415_withSafeMessage() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Unsupported content type"));
    }

    @Test
    void typeMismatch_returns400_withoutEchoingOffendingValue() throws Exception {
        mockMvc.perform(get("/test/typed/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for 'id'"))
                .andExpect(content().string(not(containsString("not-a-number"))));
    }
}
