package com.giftconnect.service;

import com.giftconnect.controller.AuthController;
import com.giftconnect.dto.ProductRequest;
import com.giftconnect.dto.ProductResponse;
import com.giftconnect.entity.Category;
import com.giftconnect.entity.Product;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.AccessDeniedException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 — seller ownership rules, exercised against a real ProductService with
 * mocked repositories (isolated test data; no database, no SMTP).
 *
 * The owner is always derived from the server-side session. A SELLER may only
 * manage rows whose seller_id matches their session user id; NULL-owned
 * (legacy/platform) and other sellers' rows are ADMIN-only.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceOwnerTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductService productService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category("Bottel", "gift bottles");
        category.setCategoryId(1L);
    }

    private HttpServletRequest request(Role role, Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, userId);
        session.setAttribute(AuthController.SESSION_USER_ROLE, role);
        session.setAttribute(AuthController.SESSION_USER_EMAIL, "actor@example.com");
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getSession(false)).thenReturn(session);
        return request;
    }

    private ProductRequest productRequest() {
        ProductRequest request = new ProductRequest();
        request.setProductName("Mug");
        request.setDescription("desc");
        request.setPrice(new BigDecimal("9.99"));
        request.setStock(5);
        request.setCategoryId(1L);
        request.setStatus("ACTIVE");
        return request;
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }

    private Product product(Long id, Long sellerId) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName("Mug");
        product.setDescription("desc");
        product.setPrice(new BigDecimal("9.99"));
        product.setStock(5);
        product.setCategory(category);
        product.setStatus("ACTIVE");
        if (sellerId != null) {
            product.setSeller(user(sellerId));
        }
        return product;
    }

    // ---------- create ----------

    @Test
    void sellerCreate_stampsSessionSellerId() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user(42L)));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.addProduct(productRequest(), request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo(42L);
    }

    @Test
    void adminCreate_leavesSellerNull() {
        HttpServletRequest request = request(Role.ADMIN, 1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.addProduct(productRequest(), request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSeller()).isNull();
    }

    // ---------- update ----------

    @Test
    void sellerUpdateOwnProduct_isAllowed() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product existing = product(5L, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(5L, productRequest(), request);

        verify(productRepository).save(existing);
    }

    @Test
    void sellerUpdateOtherSellersProduct_isDenied() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, 99L)));

        assertThatThrownBy(() -> productService.updateProduct(5L, productRequest(), request))
                .isInstanceOf(AccessDeniedException.class);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void sellerUpdateNullOwnedProduct_isDenied() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, null)));

        assertThatThrownBy(() -> productService.updateProduct(5L, productRequest(), request))
                .isInstanceOf(AccessDeniedException.class);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void adminUpdateNullOwnedProduct_isAllowed() {
        HttpServletRequest request = request(Role.ADMIN, 1L);
        Product existing = product(5L, null);
        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.updateProduct(5L, productRequest(), request);

        verify(productRepository).save(existing);
    }

    // ---------- delete ----------

    @Test
    void sellerDeleteOwnProduct_isAllowed() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product existing = product(5L, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(5L, request);

        verify(productRepository).delete(existing);
    }

    @Test
    void sellerDeleteOtherSellersProduct_isDenied() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, 99L)));

        assertThatThrownBy(() -> productService.deleteProduct(5L, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void sellerDeleteNullOwnedProduct_isDenied() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, null)));

        assertThatThrownBy(() -> productService.deleteProduct(5L, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void adminDeleteNullOwnedProduct_isAllowed() {
        HttpServletRequest request = request(Role.ADMIN, 1L);
        Product existing = product(5L, null);
        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(5L, request);

        verify(productRepository).delete(existing);
    }

    // ---------- seller-scoped listing ----------

    @Test
    void sellerList_returnsOwnProductsIncludingInactive() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product activeOwn = product(5L, 42L);
        Product inactiveOwn = product(6L, 42L);
        inactiveOwn.setStatus("INACTIVE");
        when(productRepository.findBySeller_UserId(42L)).thenReturn(List.of(activeOwn, inactiveOwn));

        List<ProductResponse> result = productService.getAllProducts(request);

        assertThat(result).extracting(ProductResponse::getProductId).containsExactly(5L, 6L);
        verify(productRepository, never()).findAll();
        verify(userRepository, never()).findById(anyLong());
    }

    // ---------- single-product visibility (Phase 3G) ----------

    @Test
    void sellerGetById_ownInactiveProduct_isVisible() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product own = product(7L, 42L);
        own.setStatus("INACTIVE");
        when(productRepository.findById(7L)).thenReturn(Optional.of(own));

        ProductResponse result = productService.getProductById(7L, request);

        assertThat(result.getProductId()).isEqualTo(7L);
    }

    @Test
    void sellerGetById_ownActiveProduct_isVisible() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        when(productRepository.findById(7L)).thenReturn(Optional.of(product(7L, 42L)));

        ProductResponse result = productService.getProductById(7L, request);

        assertThat(result.getProductId()).isEqualTo(7L);
    }

    @Test
    void sellerGetById_otherSellersInactiveProduct_is404() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product other = product(7L, 99L);
        other.setStatus("INACTIVE");
        when(productRepository.findById(7L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> productService.getProductById(7L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sellerGetById_unassignedInactiveProduct_is404() {
        HttpServletRequest request = request(Role.SELLER, 42L);
        Product unassigned = product(7L, null);
        unassigned.setStatus("INACTIVE");
        when(productRepository.findById(7L)).thenReturn(Optional.of(unassigned));

        assertThatThrownBy(() -> productService.getProductById(7L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void customerGetById_inactiveProduct_is404() {
        HttpServletRequest request = request(Role.CUSTOMER, 42L);
        Product inactive = product(7L, 42L);
        inactive.setStatus("INACTIVE");
        when(productRepository.findById(7L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> productService.getProductById(7L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
