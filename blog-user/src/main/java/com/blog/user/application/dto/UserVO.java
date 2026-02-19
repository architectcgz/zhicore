package com.blog.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 用户视图对象
 *
 * @author Blog Team
 */
@Schema(description = "用户详细信息")
@Data
public class UserVO {

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "1")
    private Long id;

    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "zhangsan")
    private String userName;

    /**
     * 昵称
     */
    @Schema(description = "昵称", example = "张三")
    private String nickName;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱地址", example = "zhangsan@example.com")
    private String email;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    /**
     * 个人简介
     */
    @Schema(description = "个人简介", example = "热爱编程的开发者")
    private String bio;

    /**
     * 用户状态
     */
    @Schema(description = "用户状态：0-正常，1-禁用", example = "0")
    private Integer status;

    /**
     * 邮箱是否已验证
     */
    @Schema(description = "邮箱是否已验证", example = "true")
    private Boolean emailConfirmed;

    /**
     * 角色列表
     */
    @Schema(description = "用户角色列表", example = "[\"USER\", \"ADMIN\"]")
    private Set<String> roles;

    /**
     * 粉丝数
     */
    @Schema(description = "粉丝数量", example = "100")
    private Integer followersCount;

    /**
     * 关注数
     */
    @Schema(description = "关注数量", example = "50")
    private Integer followingCount;

    /**
     * 创建时间
     */
    @Schema(description = "账号创建时间", example = "2024-01-01T00:00:00Z")
    private OffsetDateTime createdAt;
}
