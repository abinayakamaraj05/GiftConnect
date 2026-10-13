package com.giftconnect.service;

import com.giftconnect.controller.AuthController;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.entity.Category;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.Role;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 2 — public catalog browsing must never expose INACTIVE products to
 * non-admin (including anonymous) callers, while ADMIN still sees everything.
 * Only the repositories are mocked; no database is touched.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceCatalogVisibilityTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest anonymousRequest;
    @Mock
    private HttpServletRequest adminRequest;

    @InjectMocks
    private ProductService productService;

    private Product activeProduct;
    private Product inactiveProduct;

    @BeforeEach
    void setUp() {
        Category category = new Category("Bottel", "gift bottles");
        category.setCategoryId(1L);

        activeProduct = product(1L, "Active Mug", "ACTIVE", category);
        inactiveProduct = product(2L, "Retired Mug", "INACTIVE", category);

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute(AuthController.SESSION_USER_ID, 99L);
        adminSession.setAttribute(AuthController.SESSION_USER_ROLE, Role.ADMIN);
        lenient().when(adminRequest.getSession(false)).thenReturn(adminSession);
    }

    private Product product(Long id, String name, String status, Category category) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName(name);
        product.setDescription("desc");
        product.setPrice(new BigDecimal("9.99"));
        product.setStock(5);
        product.setCategory(category);
        product.setStatus(status);
        return product;
    }

    @Test
    void anonymousList_hidesInactiveProducts() {
        when(productRepository.findAll()).thenReturn(List.of(activeProduct, inactiveProduct));

        List<ProductResponse> result = productService.getAllProducts(anonymousRequest);

        assertThat(result).extracting(ProductResponse::getProductName).containsExactly("Active Mug");
    }

    @Test
    void adminList_includesInactiveProducts() {
        when(productRepository.findAll()).thenReturn(List.of(activeProduct, inactiveProduct));

        List<ProductResponse> result = productService.getAllProducts(adminRequest);

        assertThat(result).extracting(ProductResponse::getProductName)
                .containsExactlyInAnyOrder("Active Mug", "Retired Mug");
    }

    @Test
    void anonymousGetById_ofInactiveProduct_is404() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> productService.getProductById(2L, anonymousRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminGetById_ofInactiveProduct_isVisible() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(inactiveProduct));

        ProductResponse result = productService.getProductById(2L, adminRequest);

        assertThat(result.getProductName()).isEqualTo("Retired Mug");
    }

    @Test
    void anonymousSearch_hidesInactiveProducts() {
        when(productRepository.findByProductNameContainingIgnoreCase("mug"))
                .thenReturn(List.of(activeProduct, inactiveProduct));

        List<ProductResponse> result = productService.searchProductsByName("mug", anonymousRequest);

        assertThat(result).extracting(ProductResponse::getProductName).containsExactly("Active Mug");
    }
}
