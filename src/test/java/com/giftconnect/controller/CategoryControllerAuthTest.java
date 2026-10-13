package com.giftconnect.controller;

import com.giftconnect.entity.Category;
import com.giftconnect.entity.Role;
import com.giftconnect.service.CategoryService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules for /api/categories.
 *
 * Browsing stays open to any authenticated session; mutation is ADMIN-only.
 * These tests exercise the real AuthFilter (session -> role) and the real
 * AccessControl guards; only the service layer is mocked, so no database or
 * SMTP is touched.
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private UserService userService;

    private static final Long ACTOR_ID = 7L;
    private static final String BODY = "{\"categoryName\":\"Birthday\",\"description\":\"d\"}";

    private MockHttpSession session(Role role) {
        TestSessions.stubActiveSession(userService, ACTOR_ID, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, ACTOR_ID);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        return session;
    }

    @Test
    void create_withoutSession_is401() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asCustomer_is403() throws Exception {
        mockMvc.perform(post("/api/categories").session(session(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asSeller_is403() throws Exception {
        mockMvc.perform(post("/api/categories").session(session(Role.SELLER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asAdmin_is201() throws Exception {
        when(categoryService.addCategory(any(), any())).thenReturn(new Category("Birthday", "d"));

        mockMvc.perform(post("/api/categories").session(session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_asCustomer_is403() throws Exception {
        mockMvc.perform(delete("/api/categories/5").session(session(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asAdmin_is204() throws Exception {
        mockMvc.perform(delete("/api/categories/5").session(session(Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_asCustomer_is200() throws Exception {
        mockMvc.perform(get("/api/categories").session(session(Role.CUSTOMER)))
                .andExpect(status().isOk());
    }

    // ----- Phase 2: public catalog browsing (GET only) -----

    @Test
    void list_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void getById_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/categories/search").param("name", "birth"))
                .andExpect(status().isOk());
    }

    @Test
    void update_withoutSession_is401() throws Exception {
        mockMvc.perform(put("/api/categories/5")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
