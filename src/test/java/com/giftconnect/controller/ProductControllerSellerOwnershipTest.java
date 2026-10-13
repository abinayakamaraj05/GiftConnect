package com.giftconnect.controller;

import com.giftconnect.entity.Category;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.service.ActivityLogService;
import com.giftconnect.service.ProductService;
import com.giftconnect.service.UserService;
import com.giftconnect.util.TestSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3F — cross-seller authorization proven through the REAL ProductService
 * and the real GlobalExceptionHandler, not a mocked service hand-configured to
 * return 403.
 *
 * @WebMvcTest loads the real AuthFilter and @RestControllerAdvice; @Import adds
 * the real ProductService backed by mocked repositories. A SELLER acting on a
 * row owned by another seller therefore flows through
 * AccessControl.requireOwnerOrAdmin -> AccessDeniedException -> 403 exactly as
 * in production. No database, SMTP, or production data is involved.
 */
@WebMvcTest(ProductController.class)
@Import(ProductService.class)
class ProductControllerSellerOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;
    @MockitoBean
    private CategoryRepository categoryRepository;
    @MockitoBean
    private ActivityLogService activityLogService;
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    private static final String BODY =
            "{\"productName\":\"Mug\",\"price\":10.0,\"stock\":5,\"categoryId\":1}";
    private static final Long ACTOR_SELLER_ID = 42L;
    private static final Long OTHER_SELLER_ID = 99L;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category("Bottel", "gift bottles");
        category.setCategoryId(1L);
    }

    private MockHttpSession sellerSession(Long userId) {
        TestSessions.stubActiveSession(userService, userId, Role.SELLER);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, userId);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "seller@example.com");
        session.setAttribute(AuthController.SESSION_USER_ROLE, Role.SELLER);
        return session;
    }

    private Product productOwnedBy(Long productId, Long sellerId) {
        Product product = new Product();
        product.setProductId(productId);
        product.setProductName("Mug");
        product.setDescription("desc");
        product.setPrice(new BigDecimal("9.99"));
        product.setStock(5);
        product.setCategory(category);
        product.setStatus("ACTIVE");
        if (sellerId != null) {
            User seller = new User();
            seller.setUserId(sellerId);
            product.setSeller(seller);
        }
        return product;
    }

    @Test
    void update_otherSellersProduct_asSeller_is403() throws Exception {
        when(productRepository.findById(5L)).thenReturn(Optional.of(productOwnedBy(5L, OTHER_SELLER_ID)));

        mockMvc.perform(put("/api/products/5")
                        .session(sellerSession(ACTOR_SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_otherSellersProduct_asSeller_is403() throws Exception {
        when(productRepository.findById(5L)).thenReturn(Optional.of(productOwnedBy(5L, OTHER_SELLER_ID)));

        mockMvc.perform(delete("/api/products/5").session(sellerSession(ACTOR_SELLER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_ownProduct_asSeller_is200() throws Exception {
        // Positive control: the real service is wired and ownership is honored,
        // so the 403s above cannot be a false pass from a misconfigured context.
        Product own = productOwnedBy(5L, ACTOR_SELLER_ID);
        when(productRepository.findById(5L)).thenReturn(Optional.of(own));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/products/5")
                        .session(sellerSession(ACTOR_SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }
}
