package com.blog.common.result;

import lombok.Getter;

/**
 * 响应状态码枚举
 * 
 * 错误码格式：{服务代码}{错误类型}{序号}
 * - 服务代码：1xxx=通用，2xxx=认证，3xxx=用户，4xxx=文章，5xxx=评论，6xxx=消息，7xxx=通知，8xxx=文件
 * - HTTP状态码：200=成功，4xx=客户端错误，5xx=服务端错误
 */
@Getter
public enum ResultCode {

    // ==================== 成功 ====================
    SUCCESS(200, "操作成功"),

    // ==================== HTTP 客户端错误 4xx ====================
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // ==================== HTTP 服务端错误 5xx ====================
    FAIL(500, "操作失败"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // ==================== 通用错误 1xxx ====================
    INTERNAL_ERROR(1000, "服务器内部错误"),
    PARAM_ERROR(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必要参数"),
    REQUEST_TOO_FREQUENT(1003, "请求过于频繁"),
    SERVICE_DEGRADED(1004, "服务暂时不可用"),
    DATA_NOT_FOUND(1005, "数据不存在"),
    DATA_ALREADY_EXISTS(1006, "数据已存在"),
    OPERATION_FAILED(1007, "操作失败"),
    OPERATION_NOT_ALLOWED(1008, "操作不允许"),

    // ==================== 认证授权错误 2xxx ====================
    TOKEN_INVALID(2001, "Token无效"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    LOGIN_FAILED(2003, "登录失败"),
    ACCOUNT_DISABLED(2004, "账号已禁用"),
    PERMISSION_DENIED(2005, "权限不足"),
    LOGIN_REQUIRED(2006, "请先登录"),
    ROLE_REQUIRED(2007, "需要特定角色"),
    RESOURCE_ACCESS_DENIED(2008, "无权访问该资源"),

    // ==================== 用户服务错误 3xxx ====================
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_ALREADY_EXISTS(3002, "用户已存在"),
    PASSWORD_ERROR(3003, "密码错误"),
    EMAIL_ALREADY_EXISTS(3004, "邮箱已被注册"),
    USERNAME_ALREADY_EXISTS(3005, "用户名已被使用"),
    USER_DISABLED(3006, "用户已被禁用"),
    FOLLOW_SELF_NOT_ALLOWED(3007, "不能关注自己"),
    ALREADY_FOLLOWED(3008, "已经关注"),
    NOT_FOLLOWED(3009, "尚未关注"),
    USER_BLOCKED(3010, "用户已被拉黑"),
    BLOCK_SELF_NOT_ALLOWED(3011, "不能拉黑自己"),
    ALREADY_CHECKED_IN(3012, "今日已签到"),

    // ==================== 文章服务错误 4xxx ====================
    POST_NOT_FOUND(4001, "文章不存在"),
    POST_ALREADY_PUBLISHED(4002, "文章已发布"),
    POST_NOT_PUBLISHED(4003, "文章未发布"),
    POST_ALREADY_DELETED(4004, "文章已删除"),
    POST_TITLE_EMPTY(4005, "文章标题不能为空"),
    POST_CONTENT_EMPTY(4006, "文章内容不能为空"),
    POST_TITLE_TOO_LONG(4007, "文章标题过长"),
    POST_ALREADY_LIKED(4008, "已点赞该文章"),
    POST_NOT_LIKED(4009, "未点赞该文章"),
    POST_ALREADY_FAVORITED(4010, "已收藏该文章"),
    POST_NOT_FAVORITED(4011, "未收藏该文章"),
    CATEGORY_NOT_FOUND(4012, "分类不存在"),

    // ==================== 评论服务错误 5xxx ====================
    COMMENT_NOT_FOUND(5001, "评论不存在"),
    COMMENT_ALREADY_DELETED(5002, "评论已删除"),
    COMMENT_CONTENT_EMPTY(5003, "评论内容不能为空"),
    COMMENT_CONTENT_TOO_LONG(5004, "评论内容过长"),
    ROOT_COMMENT_NOT_FOUND(5005, "根评论不存在"),
    REPLY_TO_COMMENT_NOT_FOUND(5006, "被回复的评论不存在"),
    COMMENT_ALREADY_LIKED(5007, "已点赞该评论"),
    COMMENT_NOT_LIKED(5008, "未点赞该评论"),

    // ==================== 消息服务错误 6xxx ====================
    MESSAGE_NOT_FOUND(6001, "消息不存在"),
    CONVERSATION_NOT_FOUND(6002, "会话不存在"),
    MESSAGE_ALREADY_RECALLED(6003, "消息已撤回"),
    MESSAGE_RECALL_TIMEOUT(6004, "消息发送超过2分钟，无法撤回"),
    MESSAGE_CONTENT_EMPTY(6005, "消息内容不能为空"),
    MESSAGE_CONTENT_TOO_LONG(6006, "消息内容过长"),
    CANNOT_MESSAGE_SELF(6007, "不能给自己发消息"),
    USER_BLOCKED_CANNOT_MESSAGE(6008, "对方已将你拉黑，无法发送消息"),

    // ==================== 通知服务错误 7xxx ====================
    NOTIFICATION_NOT_FOUND(7001, "通知不存在"),

    // ==================== 文件上传错误 8xxx ====================
    FILE_TOO_LARGE(8001, "文件过大"),
    FILE_TYPE_NOT_ALLOWED(8002, "文件类型不允许"),
    UPLOAD_FAILED(8003, "上传失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
