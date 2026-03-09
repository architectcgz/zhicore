package com.zhicore.admin.infrastructure.sentinel;

/**
 * 管理服务 Sentinel 方法级资源常量。
 */
public final class AdminSentinelResources {

    private AdminSentinelResources() {
    }

    public static final String LIST_USERS = "admin:listUsers";
    public static final String LIST_POSTS = "admin:listPosts";
    public static final String LIST_COMMENTS = "admin:listComments";
    public static final String LIST_PENDING_REPORTS = "admin:listPendingReports";
    public static final String LIST_REPORTS_BY_STATUS = "admin:listReportsByStatus";
}
