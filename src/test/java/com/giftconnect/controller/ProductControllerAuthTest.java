package com.giftconnect.controller;

import com.giftconnect.entity.Role;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules for /api/products at the controller/web layer.
 *
 * Phase 3 — SELLER and ADMIN may create; update/delete are allowed through the
 * controller for SELLER and ADMIN, with per-product ownership enforced in
 * ProductService (see ProductServiceOwnerTest). CUSTOMER is blocked here with
 * 403. Public catalog GETs are exercised in the Phase 2 section below.
 */
@WebMvcTest(ProductController.class)
class ProductControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private UserService userService;

    private static final Long ACTOR_ID = 7L;
    private static final String BODY =
            "{\"productName\":\"Mug\",\"price\":10.0,\"stock\":5,\"categoryId\":1}";

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
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asCustomer_is403() throws Exception {
        mockMvc.perform(post("/api/products").session(session(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asSeller_is201() throws Exception {
        mockMvc.perform(post("/api/products").session(session(Role.SELLER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void create_asAdmin_is201() throws Exception {
        mockMvc.perform(post("/api/products").session(session(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void update_asSeller_is200() throws Exception {
        mockMvc.perform(put("/api/products/3").session(session(Role.SELLER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void update_asCustomer_is403() throws Exception {
        mockMvc.perform(put("/api/products/3").session(session(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asSeller_is204() throws Exception {
        mockMvc.perform(delete("/api/products/3").session(session(Role.SELLER)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_asCustomer_is403() throws Exception {
        mockMvc.perform(delete("/api/products/3").session(session(Role.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asAdmin_is204() throws Exception {
        mockMvc.perform(delete("/api/products/3").session(session(Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_asCustomer_is200() throws Exception {
        mockMvc.perform(get("/api/products").session(session(Role.CUSTOMER)))
                .andExpect(status().isOk());
    }

    // ----- Phase 2: public catalog browsing (GET only) -----

    @Test
    void list_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void getById_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/products/search").param("name", "mug"))
                .andExpect(status().isOk());
    }

    @Test
    void byCategory_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/products/category/1"))
                .andExpect(status().isOk());
    }

    @Test
    void availability_withoutSession_is200() throws Exception {
        mockMvc.perform(get("/api/products/1/availability"))
                .andExpect(status().isOk());
    }

    @Test
    void update_withoutSession_is401() throws Exception {
        mockMvc.perform(put("/api/products/3")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_withoutSession_is401() throws Exception {
        mockMvc.perform(delete("/api/products/3"))
                .andExpect(status().isUnauthorized());
    }
}
