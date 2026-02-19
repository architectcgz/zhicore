package com.blog.user.domain.model;

import com.blog.common.exception.DomainException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DateTimeUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 不变量在构造时和方法内保证
 *
 * @author Blog Team
 */
@Getter
public class User {

    /**
     * 用户ID（雪花ID）
     */
    private final Long id;

    /**
     * 用户名（唯一）
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 邮箱（唯一）
     */
    private String email;

    /**
     * 密码哈希
     */
    private String passwordHash;

    /**
     * 头像文件ID（UUIDv7格式）
     */
    private String avatarId;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 资料版本号（用于事件顺序性保证）
     * 
     * 注意：版本号由数据库层原子递增，不在应用层手动递增
     * 在 Repository 层的 SQL 中使用 profile_version = profile_version + 1 实现原子递增
     */
    private Long profileVersion;

    /**
     * 用户状态
     */
    private UserStatus status;

    /**
     * 邮箱是否已验证
     */
    private boolean emailConfirmed;

    /**
     * 用户角色
     */
    private Set<Role> roles;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 私有构造函数
     */
    private User(Long id, String userName, String email, String passwordHash) {
        Assert.notNull(id, "用户ID不能为空");
        Assert.isTrue(id > 0, "用户ID必须为正数");
        Assert.hasText(userName, "用户名不能为空");
        Assert.hasText(email, "邮箱不能为空");
        Assert.hasText(passwordHash, "密码哈希不能为空");

        this.id = id;
        this.userName = userName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        this.emailConfirmed = false;
        this.roles = new HashSet<>();
        this.createdAt = DateTimeUtils.nowLocal();
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 私有构造函数（用于从持久化恢复和 Jackson 反序列化）
     */
    @JsonCreator
    private User(
            @JsonProperty("id") Long id, 
            @JsonProperty("userName") String userName, 
            @JsonProperty("nickName") String nickName, 
            @JsonProperty("email") String email, 
            @JsonProperty("passwordHash") String passwordHash, 
            @JsonProperty("avatarId") String avatarId, 
            @JsonProperty("bio") String bio,
            @JsonProperty("status") UserStatus status, 
            @JsonProperty("emailConfirmed") boolean emailConfirmed, 
            @JsonProperty("roles") Set<Role> roles,
            @JsonProperty("profileVersion") Long profileVersion,
            @JsonProperty("createdAt") LocalDateTime createdAt, 
            @JsonProperty("updatedAt") LocalDateTime updatedAt) {
        this.id = id;
        this.userName = userName;
        this.nickName = nickName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.avatarId = avatarId;
        this.bio = bio;
        this.status = status;
        this.emailConfirmed = emailConfirmed;
        this.roles = roles != null ? roles : new HashSet<>();
        this.profileVersion = profileVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建新用户
     *
     * @param id 用户ID
     * @param userName 用户名
     * @param email 邮箱
     * @param passwordHash 密码哈希
     * @return 用户实例
     */
    public static User create(Long id, String userName, String email, String passwordHash) {
        User user = new User(id, userName, email, passwordHash);
        user.nickName = userName; // 默认昵称为用户名
        return user;
    }

    /**
     * 从持久化恢复用户
     *
     * @return 用户实例
     */
    public static User reconstitute(Long id, String userName, String nickName, String email,
                                    String passwordHash, String avatarId, String bio,
                                    UserStatus status, boolean emailConfirmed, Set<Role> roles,
                                    Long profileVersion,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new User(id, userName, nickName, email, passwordHash, avatarId, bio,
                status, emailConfirmed, roles, profileVersion, createdAt, updatedAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 更新个人资料
     * 
     * 注意：版本号的递增由 Repository 层的 SQL 保证原子性
     * 使用 profile_version = profile_version + 1 实现数据库层原子递增
     *
     * @param nickName 昵称
     * @param avatarId 头像文件ID
     * @param bio 个人简介
     */
    public void updateProfile(String nickName, String avatarId, String bio) {
        ensureActive();
        
        if (StringUtils.hasText(nickName)) {
            validateNickName(nickName);
            this.nickName = nickName;
        }
        if (avatarId != null) {
            this.avatarId = avatarId;
        }
        if (bio != null) {
            validateBio(bio);
            this.bio = bio;
        }
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 修改密码
     *
     * @param newPasswordHash 新密码哈希
     */
    public void changePassword(String newPasswordHash) {
        ensureActive();
        Assert.hasText(newPasswordHash, "新密码不能为空");
        this.passwordHash = newPasswordHash;
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 确认邮箱
     */
    public void confirmEmail() {
        if (this.emailConfirmed) {
            throw new DomainException("邮箱已经验证过了");
        }
        this.emailConfirmed = true;
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 禁用用户
     */
    public void disable() {
        if (this.status == UserStatus.DISABLED) {
            throw new DomainException(ResultCode.USER_DISABLED, "用户已经被禁用");
        }
        this.status = UserStatus.DISABLED;
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 启用用户
     */
    public void enable() {
        if (this.status == UserStatus.ACTIVE) {
            throw new DomainException("用户已经是启用状态");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 分配角色
     *
     * @param role 角色
     */
    public void assignRole(Role role) {
        Assert.notNull(role, "角色不能为空");
        this.roles.add(role);
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 移除角色
     *
     * @param role 角色
     */
    public void removeRole(Role role) {
        Assert.notNull(role, "角色不能为空");
        this.roles.remove(role);
        this.updatedAt = DateTimeUtils.nowLocal();
    }

    /**
     * 检查是否拥有指定角色
     *
     * @param roleName 角色名称
     * @return 是否拥有
     */
    public boolean hasRole(String roleName) {
        return this.roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * 检查用户是否处于活跃状态
     *
     * @return 是否活跃
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * 设置资料版本号
     * 
     * 此方法仅供 Repository 层在更新后设置新版本号使用
     * 不应在业务逻辑中直接调用
     *
     * @param profileVersion 新的版本号
     */
    public void setProfileVersion(Long profileVersion) {
        this.profileVersion = profileVersion;
    }

    // ==================== 私有方法 ====================

    /**
     * 确保用户处于活跃状态
     */
    private void ensureActive() {
        if (!isActive()) {
            throw new DomainException(ResultCode.USER_DISABLED, "用户已被禁用，无法执行此操作");
        }
    }

    /**
     * 验证昵称
     */
    private void validateNickName(String nickName) {
        if (nickName.length() > 50) {
            throw new DomainException("昵称长度不能超过50个字符");
        }
    }

    /**
     * 验证个人简介
     */
    private void validateBio(String bio) {
        if (bio.length() > 500) {
            throw new DomainException("个人简介长度不能超过500个字符");
        }
    }
}
