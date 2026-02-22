package com.zhicore.content.domain.model;

import com.zhicore.common.exception.DomainException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 文章聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 状态机控制文章生命周期
 *
 * @author ZhiCore Team
 */
@Getter
public class Post {

    /**
     * 文章ID（雪花ID）
     */
    private final Long id;

    /**
     * 作者ID
     */
    private final Long ownerId;

    /**
     * 作者昵称快照（冗余字段）
     * 
     * 用于提升查询性能，避免每次查询文章列表时都需要关联查询用户表。
     * 当用户修改昵称时，通过消息队列异步更新此字段。
     */
    private String ownerName;

    /**
     * 作者头像文件ID快照（冗余字段）
     * 
     * 用于提升查询性能，存储用户头像的文件ID（UUIDv7格式）。
     * 当用户修改头像时，通过消息队列异步更新此字段。
     */
    private String ownerAvatarId;

    /**
     * 作者资料版本号（用于防止消息乱序）
     * 
     * 记录作者资料的版本号，用于实现幂等性和防止消息乱序导致的数据不一致。
     * 只有当接收到的版本号大于当前版本号时，才更新作者信息冗余字段。
     */
    private Long ownerProfileVersion;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 封面图文件ID（用于引用 file-service，UUIDv7格式）
     */
    private String coverImageId;

    /**
     * 文章状态
     */
    private PostStatus status;

    /**
     * 话题ID
     */
    private Long topicId;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 定时发布时间
     */
    private LocalDateTime scheduledAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 是否已归档
     */
    private Boolean isArchived;

    /**
     * 文章统计
     */
    private PostStats stats;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数（创建新文章）
     */
    private Post(Long id, Long ownerId, String title) {
        Assert.notNull(id, "文章ID不能为空");
        Assert.isTrue(id > 0, "文章ID必须为正数");
        Assert.notNull(ownerId, "作者ID不能为空");
        Assert.isTrue(ownerId > 0, "作者ID必须为正数");
        Assert.hasText(title, "标题不能为空");

        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.status = PostStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isArchived = false;
        this.stats = PostStats.empty();
    }


    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Post(@JsonProperty("id") Long id,
                 @JsonProperty("ownerId") Long ownerId,
                 @JsonProperty("ownerName") String ownerName,
                 @JsonProperty("ownerAvatarId") String ownerAvatarId,
                 @JsonProperty("ownerProfileVersion") Long ownerProfileVersion,
                 @JsonProperty("title") String title,
                 @JsonProperty("excerpt") String excerpt,
                 @JsonProperty("coverImageId") String coverImageId,
                 @JsonProperty("status") PostStatus status,
                 @JsonProperty("topicId") Long topicId,
                 @JsonProperty("publishedAt") LocalDateTime publishedAt,
                 @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
                 @JsonProperty("createdAt") LocalDateTime createdAt,
                 @JsonProperty("updatedAt") LocalDateTime updatedAt,
                 @JsonProperty("isArchived") Boolean isArchived,
                 @JsonProperty("stats") PostStats stats) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerAvatarId = ownerAvatarId;
        this.ownerProfileVersion = ownerProfileVersion;
        this.title = title;
        this.excerpt = excerpt;
        this.coverImageId = coverImageId;
        this.status = status;
        this.topicId = topicId;
        this.publishedAt = publishedAt;
        this.scheduledAt = scheduledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isArchived = isArchived != null ? isArchived : false;
        this.stats = stats != null ? stats : PostStats.empty();
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建草稿
     *
     * @param id 文章ID
     * @param ownerId 作者ID
     * @param title 标题
     * @return 文章实例
     */
    public static Post createDraft(Long id, Long ownerId, String title) {
        Post post = new Post(id, ownerId, title);
        return post;
    }

    /**
     * 从持久化恢复文章
     */
    public static Post reconstitute(Long id, Long ownerId, String title,
                                    String excerpt, String coverImageId, PostStatus status, Long topicId,
                                    LocalDateTime publishedAt, LocalDateTime scheduledAt,
                                    LocalDateTime createdAt, LocalDateTime updatedAt, 
                                    Boolean isArchived, PostStats stats,
                                    String ownerName, String ownerAvatarId, Long ownerProfileVersion) {
        return new Post(id, ownerId, ownerName, ownerAvatarId, ownerProfileVersion,
                title, excerpt, coverImageId, status, topicId,
                publishedAt, scheduledAt, createdAt, updatedAt, isArchived, stats);
    }

    // ==================== 领域行为 ====================

    /**
     * 更新文章内容
     * 注意：内容现在存储在MongoDB中，这里只更新标题和摘要
     *
     * @param title 标题
     * @param excerpt 摘要
     */
    public void updateContent(String title, String excerpt) {
        ensureEditable();
        
        if (StringUtils.hasText(title)) {
            validateTitle(title);
            this.title = title;
        }
        if (excerpt != null) {
            this.excerpt = excerpt;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置话题
     *
     * @param topicId 话题ID
     */
    public void setTopic(Long topicId) {
        this.topicId = topicId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置封面图
     *
     * @param coverImageId 封面图文件ID
     */
    public void setCoverImage(String coverImageId) {
        this.coverImageId = coverImageId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 发布文章
     */
    public void publish() {
        if (this.status == PostStatus.PUBLISHED) {
            throw new DomainException("文章已经发布，不能重复发布");
        }
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("已删除的文章不能发布");
        }
        validateForPublish();

        this.status = PostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.scheduledAt = null;
    }

    /**
     * 定时发布
     *
     * @param scheduledAt 定时发布时间
     */
    public void schedulePublish(LocalDateTime scheduledAt) {
        if (this.status != PostStatus.DRAFT) {
            throw new DomainException("只有草稿状态的文章可以设置定时发布");
        }
        if (scheduledAt == null) {
            throw new DomainException("定时发布时间不能为空");
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new DomainException("定时发布时间不能早于当前时间");
        }
        validateForPublish();

        this.status = PostStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 执行定时发布（由定时任务调用）
     */
    public void executeScheduledPublish() {
        if (this.status != PostStatus.SCHEDULED) {
            throw new DomainException("只有定时发布状态的文章可以执行发布");
        }
        
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.scheduledAt = null;
    }

    /**
     * 撤回文章（取消发布）
     */
    public void unpublish() {
        if (this.status != PostStatus.PUBLISHED) {
            throw new DomainException("只有已发布的文章可以撤回");
        }
        this.status = PostStatus.DRAFT;
        this.publishedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 取消定时发布
     */
    public void cancelSchedule() {
        if (this.status != PostStatus.SCHEDULED) {
            throw new DomainException("只有定时发布状态的文章可以取消定时");
        }
        this.status = PostStatus.DRAFT;
        this.scheduledAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除文章
     */
    public void delete() {
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("文章已经删除");
        }
        this.status = PostStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== 查询方法 ====================

    /**
     * 检查是否可以发布
     */
    public boolean canBePublished() {
        return (this.status == PostStatus.DRAFT || this.status == PostStatus.SCHEDULED) &&
                StringUtils.hasText(this.title);
    }

    /**
     * 检查是否为指定用户所有
     */
    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    /**
     * 检查是否已发布
     */
    public boolean isPublished() {
        return this.status == PostStatus.PUBLISHED;
    }

    /**
     * 检查是否为草稿
     */
    public boolean isDraft() {
        return this.status == PostStatus.DRAFT;
    }

    /**
     * 检查是否已删除
     */
    public boolean isDeleted() {
        return this.status == PostStatus.DELETED;
    }

    /**
     * 检查是否已归档
     */
    public boolean isArchived() {
        return this.isArchived != null && this.isArchived;
    }

    /**
     * 标记为已归档
     */
    public void markAsArchived() {
        this.isArchived = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 取消归档标记
     */
    public void unmarkArchived() {
        this.isArchived = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查作者信息是否为默认值（未知用户）
     * 
     * 用于补偿机制和回填任务，判断文章的作者信息是否需要更新。
     * 当创建文章时 user-service 不可用，会使用默认值，后续需要补偿更新。
     * 
     * @return true 如果作者信息为默认值，false 如果已经更新过
     */
    public boolean hasDefaultAuthorInfo() {
        return "未知用户".equals(this.ownerName) && 
               (this.ownerProfileVersion == null || this.ownerProfileVersion == 0L);
    }

    // ==================== 作者信息更新方法 ====================

    /**
     * 更新作者信息冗余字段
     * 
     * 用于处理用户资料更新事件，更新文章中的作者信息快照。
     * 使用版本号机制防止消息乱序导致的数据不一致。
     *
     * @param ownerName 作者昵称
     * @param ownerAvatarId 作者头像文件ID
     * @param ownerProfileVersion 作者资料版本号
     * @return 是否更新成功（版本号检查通过）
     */
    public boolean updateOwnerInfo(String ownerName, String ownerAvatarId, Long ownerProfileVersion) {
        // 版本号检查：只有当新版本号大于当前版本号时才更新
        if (this.ownerProfileVersion != null && ownerProfileVersion <= this.ownerProfileVersion) {
            return false;
        }
        
        this.ownerName = ownerName;
        this.ownerAvatarId = ownerAvatarId;
        this.ownerProfileVersion = ownerProfileVersion;
        this.updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 设置作者信息（用于创建文章时初始化）
     * 
     * @param ownerName 作者昵称
     * @param ownerAvatarId 作者头像文件ID
     * @param ownerProfileVersion 作者资料版本号
     */
    public void setOwnerInfo(String ownerName, String ownerAvatarId, Long ownerProfileVersion) {
        this.ownerName = ownerName;
        this.ownerAvatarId = ownerAvatarId;
        this.ownerProfileVersion = ownerProfileVersion;
    }

    // ==================== 私有方法 ====================

    /**
     * 确保文章可编辑
     */
    private void ensureEditable() {
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("已删除的文章不能编辑");
        }
    }

    /**
     * 验证标题
     */
    private void validateTitle(String title) {
        if (title.length() > 200) {
            throw new DomainException("文章标题不能超过200字");
        }
    }

    /**
     * 验证发布条件
     */
    private void validateForPublish() {
        if (!StringUtils.hasText(this.title)) {
            throw new DomainException("文章标题不能为空");
        }
        if (this.title.length() > 200) {
            throw new DomainException("文章标题不能超过200字");
        }
        // 注意：内容验证现在由调用方在MongoDB层面处理
    }

    /**
     * 生成摘要
     */
    public static String generateExcerpt(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 移除HTML标签和多余空白
        String plainText = content.replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (plainText.length() <= maxLength) {
            return plainText;
        }
        return plainText.substring(0, maxLength) + "...";
    }
}
