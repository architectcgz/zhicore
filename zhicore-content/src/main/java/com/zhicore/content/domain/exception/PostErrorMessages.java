package com.zhicore.content.domain.exception;

/**
 * 文章模块错误消息常量
 *
 * 统一管理 Handler 层的业务校验错误消息，避免硬编码散落在各处
 */
public final class PostErrorMessages {

    private PostErrorMessages() {}

    // ===== 权限校验 =====
    public static final String NOT_OWNER_UPDATE = "无权更新此文章：用户不是文章所有者";
    public static final String NOT_OWNER_UPDATE_CONTENT = "无权更新此文章内容：用户不是文章所有者";
    public static final String NOT_OWNER_UPDATE_TAGS = "无权更新此文章：用户不是文章所有者";
    public static final String NOT_OWNER_DELETE = "无权删除此文章：用户不是文章所有者";
    public static final String NOT_OWNER_RESTORE = "无权恢复此文章：用户不是文章所有者";
    public static final String NOT_OWNER_SCHEDULE = "无权设置定时发布：用户不是文章所有者";
    public static final String NOT_OWNER_UNPUBLISH = "无权撤回此文章：用户不是文章所有者";
    public static final String NOT_OWNER_CANCEL_SCHEDULE = "无权取消定时发布：用户不是文章所有者";

    // ===== 状态校验 =====
    public static final String CANNOT_UPDATE_DELETED = "无法更新已删除的文章";
    public static final String NOT_PUBLISHED_STATUS = "文章不是已发布状态，无法撤回：当前状态=";
    public static final String NOT_SCHEDULED_STATUS = "文章不是定时发布状态，无法取消：当前状态=";
    public static final String SCHEDULE_TIME_MUST_FUTURE = "定时发布时间必须在未来";
    public static final String ONLY_ADMIN_PURGE_NOT_DELETED = "只有管理员可以物理删除未软删除的文章";

    // ===== 操作失败 =====
    public static final String UPDATE_CONTENT_FAILED = "更新文章内容失败";
    public static final String DELETE_CONTENT_FAILED = "删除文章内容失败";
}
