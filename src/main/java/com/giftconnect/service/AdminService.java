package com.giftconnect.service;

import com.giftconnect.dto.ActivityLogResponse;
import com.giftconnect.dto.PagedResponse;
import com.giftconnect.dto.SystemOverviewResponse;
import com.giftconnect.dto.UserResponse;
import com.giftconnect.entity.AccountStatus;
import com.giftconnect.entity.ActivityStatus;
import com.giftconnect.entity.ActivityType;
import com.giftconnect.entity.Role;
import com.giftconnect.entity.User;
import com.giftconnect.exception.InvalidOperationException;
import com.giftconnect.exception.ResourceNotFoundException;
import com.giftconnect.repository.CategoryRepository;
import com.giftconnect.repository.OrderRepository;
import com.giftconnect.repository.PaymentRepository;
import com.giftconnect.repository.ProductRepository;
import com.giftconnect.repository.UserRepository;
import com.giftconnect.util.SessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ADMIN-facing service: the system overview, the filterable audit trail, and
 * (Phase 5) user management. Lives in its own service so AdminController stays a
 * thin HTTP layer, matching how every other module in this project is wired.
 *
 * The audit trail itself is READ-ONLY here — nothing can create, edit, or delete
 * an ActivityLog row, so the trail stays append-only. The user-management
 * methods below CAN change a user's status or role, but every one of them is
 * guarded (self-change, last-active-ADMIN) and audited.
 */
@Service
public class AdminService {

    /** How many rows each overview panel shows. */
    private static final int RECENT_LIMIT = 10;

    /** Upper bound for a single user-list page (same cap as the audit trail). */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ActivityLogService activityLogService;

    @Autowired
    public AdminService(UserRepository userRepository,
                        CategoryRepository categoryRepository,
                        ProductRepository productRepository,
                        OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.activityLogService = activityLogService;
    }

    /**
     * Platform-wide counts + the newest audit rows, all in ONE response so the
     * dashboard does not have to fan out into eight requests. Every number is a
     * COUNT query — no table is loaded into memory.
     */
    public SystemOverviewResponse getOverview() {
        SystemOverviewResponse overview = new SystemOverviewResponse();

        overview.setTotalUsers(userRepository.count());
        overview.setTotalCustomers(userRepository.countByRole(Role.CUSTOMER));
        overview.setTotalSellers(userRepository.countByRole(Role.SELLER));
        overview.setTotalAdmins(userRepository.countByRole(Role.ADMIN));
        overview.setTotalActiveUsers(userRepository.countByAccountStatus(AccountStatus.ACTIVE));
        overview.setTotalInactiveUsers(userRepository.countByAccountStatus(AccountStatus.INACTIVE));

        overview.setTotalCategories(categoryRepository.count());
        overview.setTotalProducts(productRepository.count());
        overview.setTotalOrders(orderRepository.count());
        overview.setTotalPayments(paymentRepository.count());

        overview.setTotalActivityLogs(activityLogService.countAllLogs());
        overview.setFailedAuthEvents(activityLogService.countFailedAuthEvents());

        overview.setRecentActivity(activityLogService.getRecentLogs(RECENT_LIMIT)
                .getContent().stream()
                .map(ActivityLogResponse::fromEntity)
                .toList());
        overview.setRecentAuthEvents(activityLogService.getRecentAuthEvents(RECENT_LIMIT)
                .getContent().stream()
                .map(ActivityLogResponse::fromEntity)
                .toList());

        return overview;
    }

    /**
     * Paged audit trail with optional filters. Every filter is nullable, so the
     * database (not this method) decides what matches, and page/size are clamped
     * inside ActivityLogService (max 100 rows per page).
     */
    public PagedResponse<ActivityLogResponse> getActivityLog(Long userId,
                                                             Role role,
                                                             ActivityType type,
                                                             String entityType,
                                                             ActivityStatus status,
                                                             LocalDateTime from,
                                                             LocalDateTime to,
                                                             int page,
                                                             int size) {
        return PagedResponse.from(
                activityLogService.getFilteredLogs(userId, role, type, entityType, status, from, to, page, size),
                ActivityLogResponse::fromEntity);
    }

    // ==================================================================
    // Phase 5 — ADMIN user management
    // ==================================================================

    /**
     * Paged, filterable user list for the admin console. Search matches name OR
     * email (case-insensitive); role and status are optional filters. Filtering
     * and paging happen in the database, never by loading the table into memory.
     *
     * Listing is deliberately NOT audited (it would flood the trail); opening one
     * specific user IS — see getUserDetail.
     */
    public PagedResponse<UserResponse> getUserPage(String search,
                                                   Role role,
                                                   AccountStatus status,
                                                   int page,
                                                   int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        return PagedResponse.from(
                userRepository.findByFilters(cleanSearch, role, status, pageable),
                UserResponse::fromEntity);
    }

    /**
     * One user's safe profile — never credentials (see UserResponse).
     * Throws ResourceNotFoundException, which GlobalExceptionHandler maps to 404.
     */
    public UserResponse getUserDetail(Long userId, HttpServletRequest request) {
        User user = findUserOrThrow(userId);

        activityLogService.log(ActivityType.ADMIN_USER_VIEWED, ActivityStatus.SUCCESS,
                request, "USER", user.getUserId(),
                "Admin viewed user #" + user.getUserId() + " (" + user.getEmail() + ")");

        return UserResponse.fromEntity(user);
    }

    /**
     * Activates or deactivates an account. Nothing is deleted — the account, its
     * orders, and its payments are untouched; only the ability to log in changes.
     *
     * Guards: no self-deactivation, and the last usable ADMIN can never be
     * deactivated. The acting admin comes from the session, never the body.
     */
    @Transactional
    public UserResponse changeStatus(Long userId, AccountStatus newStatus, HttpServletRequest request) {
        User target = findUserOrThrow(userId);

        if (target.getAccountStatus() == newStatus) {
            throw new InvalidOperationException("User #" + userId + " is already " + newStatus + ".");
        }

        Long actingAdminId = SessionActor.from(request).userId();
        if (newStatus == AccountStatus.INACTIVE && userId.equals(actingAdminId)) {
            throw new InvalidOperationException("You cannot deactivate your own account.");
        }

        if (newStatus == AccountStatus.INACTIVE
                && target.getRole() == Role.ADMIN
                && userRepository.countByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new InvalidOperationException(
                    "This is the last active ADMIN. Deactivating it would lock every administrator out.");
        }

        target.setAccountStatus(newStatus);
        User saved = userRepository.save(target);

        activityLogService.log(ActivityType.ADMIN_USER_STATUS_CHANGED, ActivityStatus.SUCCESS,
                request, "USER", saved.getUserId(),
                "Account status changed to " + newStatus + " for user #" + saved.getUserId()
                        + " (" + saved.getEmail() + ")");

        return UserResponse.fromEntity(saved);
    }

    /**
     * Changes a user's role. The value is bound to the existing Role enum, so no
     * new role can be invented from the client.
     *
     * Guards: no self-role-change (this also blocks an admin promoting
     * themselves), and the last usable ADMIN cannot be demoted.
     */
    @Transactional
    public UserResponse changeRole(Long userId, Role newRole, HttpServletRequest request) {
        User target = findUserOrThrow(userId);
        Role previousRole = target.getRole();

        if (previousRole == newRole) {
            throw new InvalidOperationException("User #" + userId + " already has the role " + newRole + ".");
        }

        Long actingAdminId = SessionActor.from(request).userId();
        if (userId.equals(actingAdminId)) {
            throw new InvalidOperationException("You cannot change your own role.");
        }

        if (previousRole == Role.ADMIN
                && userRepository.countByRoleAndAccountStatus(Role.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new InvalidOperationException(
                    "This is the last active ADMIN. Changing its role would lock every administrator out.");
        }

        target.setRole(newRole);
        User saved = userRepository.save(target);

        activityLogService.log(ActivityType.ADMIN_USER_ROLE_CHANGED, ActivityStatus.SUCCESS,
                request, "USER", saved.getUserId(),
                "Role changed from " + previousRole + " to " + newRole
                        + " for user #" + saved.getUserId() + " (" + saved.getEmail() + ")");

        return UserResponse.fromEntity(saved);
    }

    /** Shared 404 lookup for the user-management endpoints. */
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
