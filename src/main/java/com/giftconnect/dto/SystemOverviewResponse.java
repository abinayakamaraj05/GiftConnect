package com.giftconnect.dto;

import java.util.List;

/**
 * The ADMIN "system overview": platform-wide counts plus a short look at the
 * most recent activity, so the dashboard needs ONE request instead of eight.
 *
 * Counts are produced by COUNT queries (never by loading rows into memory).
 */
public class SystemOverviewResponse {

    // ----- accounts -----
    private long totalUsers;
    private long totalCustomers;
    private long totalSellers;
    private long totalAdmins;
    /** Phase 5: how many accounts may actually log in, and how many are deactivated. */
    private long totalActiveUsers;
    private long totalInactiveUsers;

    // ----- catalogue / commerce -----
    private long totalCategories;
    private long totalProducts;
    private long totalOrders;
    private long totalPayments;

    // ----- audit trail -----
    private long totalActivityLogs;
    /** Security signal: failed logins + failed OTP attempts. */
    private long failedAuthEvents;
    private List<ActivityLogResponse> recentActivity;
    private List<ActivityLogResponse> recentAuthEvents;

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long totalCustomers) { this.totalCustomers = totalCustomers; }

    public long getTotalSellers() { return totalSellers; }
    public void setTotalSellers(long totalSellers) { this.totalSellers = totalSellers; }

    public long getTotalAdmins() { return totalAdmins; }
    public void setTotalAdmins(long totalAdmins) { this.totalAdmins = totalAdmins; }

    public long getTotalActiveUsers() { return totalActiveUsers; }
    public void setTotalActiveUsers(long totalActiveUsers) { this.totalActiveUsers = totalActiveUsers; }

    public long getTotalInactiveUsers() { return totalInactiveUsers; }
    public void setTotalInactiveUsers(long totalInactiveUsers) { this.totalInactiveUsers = totalInactiveUsers; }

    public long getTotalCategories() { return totalCategories; }
    public void setTotalCategories(long totalCategories) { this.totalCategories = totalCategories; }

    public long getTotalProducts() { return totalProducts; }
    public void setTotalProducts(long totalProducts) { this.totalProducts = totalProducts; }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public long getTotalPayments() { return totalPayments; }
    public void setTotalPayments(long totalPayments) { this.totalPayments = totalPayments; }

    public long getTotalActivityLogs() { return totalActivityLogs; }
    public void setTotalActivityLogs(long totalActivityLogs) { this.totalActivityLogs = totalActivityLogs; }

    public long getFailedAuthEvents() { return failedAuthEvents; }
    public void setFailedAuthEvents(long failedAuthEvents) { this.failedAuthEvents = failedAuthEvents; }

    public List<ActivityLogResponse> getRecentActivity() { return recentActivity; }
    public void setRecentActivity(List<ActivityLogResponse> recentActivity) { this.recentActivity = recentActivity; }

    public List<ActivityLogResponse> getRecentAuthEvents() { return recentAuthEvents; }
    public void setRecentAuthEvents(List<ActivityLogResponse> recentAuthEvents) { this.recentAuthEvents = recentAuthEvents; }
}
