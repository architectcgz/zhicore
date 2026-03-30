package com.zhicore.content.domain.model;

import com.zhicore.common.exception.DomainException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文章聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 状态机控制文章生命周期
 * 4. 使用值对象 ID 提供类型安全
 *
 * @author ZhiCore Team
 */
@Getter
public class Post {

    /**
     * 聚合恢复快照。
     *
     * 将持久化恢复所需字段收口，避免长参数列表继续向调用方泄漏。
     */
    public record Snapshot(
        PostId id,
        UserId ownerId,
        OwnerSnapshot ownerSnapshot,
        String title,
        String excerpt,
        String coverImageId,
        PostStatus status,
        TopicId topicId,
        Set<TagId> tagIds,
        OffsetDateTime publishedAt,
        OffsetDateTime scheduledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Boolean isArchived,
        PostStats stats,
        WriteState writeState,
        String incompleteReason,
        Long version
    ) {
    }

    /** HTML 标签 */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    /** 连续空白字符 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * 文章ID（值对象）
     */
    private final PostId id;

    /**
     * 作者ID（值对象）
     */
    private final UserId ownerId;

    /**
     * 作者信息快照
     * 
     * 包含作者昵称、头像和资料版本号。
     * 用于提升查询性能，避免每次查询文章列表时都需要关联查询用户表。
     */
    private OwnerSnapshot ownerSnapshot;

    /**
     * 写入状态（用于三阶段写入流程跟踪）
     * 
     * 跟踪文章在 PostgreSQL 和 MongoDB 中的写入进度。
     * 用于补偿机制，确保数据最终一致性。
     */
    private WriteState writeState;

    /**
     * 不完整原因（当 writeState 为 INCOMPLETE 时记录）
     */
    private String incompleteReason;

    /**
     * 创建时间
     */
    private final OffsetDateTime createdAt;

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
     * 话题ID（值对象）
     */
    private TopicId topicId;

    /**
     * 标签ID集合（值对象）
     */
    private Set<TagId> tagIds;

    /**
     * 发布时间
     */
    private OffsetDateTime publishedAt;

    /**
     * 定时发布时间
     */
    private OffsetDateTime scheduledAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;

    /**
     * 是否已归档
     */
    private Boolean isArchived;

    /**
     * 文章统计
     */
    private PostStats stats;

    /**
     * 乐观锁版本号
     * 
     * 用于并发控制，防止更新冲突
     */
    private Long version;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数（创建新文章）
     */
    private Post(PostId id, UserId ownerId, String title) {
        Assert.notNull(id, "文章ID不能为空");
        Assert.notNull(ownerId, "作者ID不能为空");
        Assert.hasText(title, "标题不能为空");

        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.status = PostStatus.DRAFT;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.isArchived = false;
        this.stats = PostStats.empty(id);
        this.writeState = WriteState.NONE;
        this.ownerSnapshot = OwnerSnapshot.createDefault(ownerId);
        this.tagIds = new HashSet<>();
    }


    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Post(@JsonProperty("id") PostId id,
                 @JsonProperty("ownerId") UserId ownerId,
                 @JsonProperty("ownerSnapshot") OwnerSnapshot ownerSnapshot,
                 @JsonProperty("title") String title,
                 @JsonProperty("excerpt") String excerpt,
                 @JsonProperty("coverImageId") String coverImageId,
                 @JsonProperty("status") PostStatus status,
                 @JsonProperty("topicId") TopicId topicId,
                 @JsonProperty("tagIds") Set<TagId> tagIds,
                 @JsonProperty("publishedAt") OffsetDateTime publishedAt,
                 @JsonProperty("scheduledAt") OffsetDateTime scheduledAt,
                 @JsonProperty("createdAt") OffsetDateTime createdAt,
                 @JsonProperty("updatedAt") OffsetDateTime updatedAt,
                 @JsonProperty("isArchived") Boolean isArchived,
                 @JsonProperty("stats") PostStats stats,
                 @JsonProperty("writeState") WriteState writeState,
                 @JsonProperty("incompleteReason") String incompleteReason,
                 @JsonProperty("version") Long version) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerSnapshot = ownerSnapshot != null ? ownerSnapshot : OwnerSnapshot.createDefault(ownerId);
        this.title = title;
        this.excerpt = excerpt;
        this.coverImageId = coverImageId;
        this.status = status;
        this.topicId = topicId;
        this.tagIds = tagIds != null ? tagIds : new HashSet<>();
        this.publishedAt = publishedAt;
        this.scheduledAt = scheduledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isArchived = isArchived != null ? isArchived : false;
        this.stats = stats != null ? stats : PostStats.empty(id);
        this.writeState = writeState != null ? writeState : WriteState.NONE;
        this.incompleteReason = incompleteReason;
        this.version = version;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建草稿
     *
     * @param id 文章ID（值对象）
     * @param ownerId 作者ID（值对象）
     * @param title 标题
     * @return 文章实例
     */
    public static Post createDraft(PostId id, UserId ownerId, String title) {
        return new Post(id, ownerId, title);
    }

    /**
     * 从持久化恢复文章
     */
    public static Post reconstitute(Snapshot snapshot) {
        Assert.notNull(snapshot, "文章恢复快照不能为空");
        return new Post(snapshot.id(), snapshot.ownerId(), snapshot.ownerSnapshot(),
                snapshot.title(), snapshot.excerpt(), snapshot.coverImageId(),
                snapshot.status(), snapshot.topicId(), snapshot.tagIds(),
                snapshot.publishedAt(), snapshot.scheduledAt(),
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.isArchived(),
                snapshot.stats(), snapshot.writeState(), snapshot.incompleteReason(),
                snapshot.version());
    }

    // ==================== 领域行为 ====================

    /**
     * 更新文章元数据
     * 
     * @param title 标题
     * @param excerpt 摘要
     * @param coverImageId 封面图文件ID
     */
    public void updateMeta(String title, String excerpt, String coverImageId) {
        ensureEditable();
        
        if (title != null && !title.isEmpty()) {
            validateTitle(title);
            this.title = title;
        }
        if (excerpt != null) {
            this.excerpt = excerpt;
        }
        if (coverImageId != null) {
            this.coverImageId = coverImageId;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 更新文章标签
     * 
     * @param newTagIds 新的标签ID集合（值对象）
     */
    public void updateTags(Set<TagId> newTagIds) {
        ensureEditable();
        this.tagIds = new HashSet<>(newTagIds);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 设置话题
     *
     * @param topicId 话题ID（值对象）
     */
    public void setTopic(TopicId topicId) {
        this.topicId = topicId;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 设置封面图
     *
     * @param coverImageId 封面图文件ID
     */
    public void setCoverImage(String coverImageId) {
        this.coverImageId = coverImageId;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 发布文章
     * 
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
        this.publishedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.scheduledAt = null;
    }

    /**
     * 定时发布
     *
     * @param scheduledAt 定时发布时间
     */
    public void schedulePublish(OffsetDateTime scheduledAt) {
        if (this.status != PostStatus.DRAFT) {
            throw new DomainException("只有草稿状态的文章可以设置定时发布");
        }
        if (scheduledAt == null) {
            throw new DomainException("定时发布时间不能为空");
        }
        if (scheduledAt.isBefore(OffsetDateTime.now())) {
            throw new DomainException("定时发布时间不能早于当前时间");
        }
        validateForPublish();

        this.status = PostStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 执行定时发布（由定时任务调用）
     */
    public void executeScheduledPublish() {
        if (this.status != PostStatus.SCHEDULED) {
            throw new DomainException("只有定时发布状态的文章可以执行发布");
        }
        
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
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
        this.updatedAt = OffsetDateTime.now();
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
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 软删除文章
     */
    public void delete() {
        if (this.status == PostStatus.DELETED) {
            throw new DomainException("文章已经删除");
        }
        this.status = PostStatus.DELETED;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 恢复已删除的文章
     */
    public void restore() {
        if (this.status != PostStatus.DELETED) {
            throw new DomainException("只有已删除的文章可以恢复");
        }
        // 恢复到草稿状态
        this.status = PostStatus.DRAFT;
        this.updatedAt = OffsetDateTime.now();
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
     * 
     * @param userId 用户ID（值对象）
     * @return 是否拥有
     */
    public boolean isOwnedBy(UserId userId) {
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
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 取消归档标记
     */
    public void unmarkArchived() {
        this.isArchived = false;
        this.updatedAt = OffsetDateTime.now();
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
        return ownerSnapshot != null && ownerSnapshot.isDefault();
    }

    /**
     * 触发更新时间（不改变业务字段）
     */
    public void touch() {
        ensureEditable();
        this.updatedAt = OffsetDateTime.now();
    }

    // ==================== 作者信息更新方法 ====================

    /**
     * 更新作者信息快照
     * 
     * 用于处理用户资料更新事件，更新文章中的作者信息快照。
     * 使用版本号机制防止消息乱序导致的数据不一致。
     *
     * @param newSnapshot 新的作者信息快照
     * @return 是否更新成功（版本号检查通过）
     */
    public boolean updateOwnerSnapshot(OwnerSnapshot newSnapshot) {
        if (newSnapshot == null) {
            return false;
        }
        
        // 版本号检查：只有当新版本号大于当前版本号时才更新
        if (this.ownerSnapshot != null && !newSnapshot.isNewerThan(this.ownerSnapshot)) {
            return false;
        }
        
        this.ownerSnapshot = newSnapshot;
        this.updatedAt = OffsetDateTime.now();
        return true;
    }

    /**
     * 设置作者信息快照（用于创建文章时初始化）
     * 
     * @param ownerSnapshot 作者信息快照
     */
    public void setOwnerSnapshot(OwnerSnapshot ownerSnapshot) {
        this.ownerSnapshot = ownerSnapshot;
    }

    // ==================== 写入状态管理方法 ====================

    /**
     * 设置写入状态
     * 
     * @param writeState 写入状态
     */
    public void setWriteState(WriteState writeState) {
        this.writeState = writeState;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 标记为不完整状态
     * 
     * @param reason 不完整原因
     */
    public void markAsIncomplete(String reason) {
        this.writeState = WriteState.INCOMPLETE;
        this.incompleteReason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 检查是否为不完整状态
     * 
     * @return true 如果为不完整状态
     */
    public boolean isIncomplete() {
        return this.writeState == WriteState.INCOMPLETE;
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
        String plainText = WHITESPACE.matcher(
                HTML_TAG.matcher(content).replaceAll("")
        ).replaceAll(" ").trim();
        if (plainText.length() <= maxLength) {
            return plainText;
        }
        return plainText.substring(0, maxLength) + "...";
    }
}
