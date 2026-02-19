package com.blog.comment.domain.model;

import com.blog.common.exception.DomainException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 评论聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 支持嵌套回复（扁平化结构）
 *
 * @author Blog Team
 */
@Getter
public class Comment {

    /**
     * 评论ID（雪花ID）
     */
    private final Long id;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 父评论ID（null表示顶级评论，回复指向顶级评论）
     */
    private Long parentId;

    /**
     * 根评论ID（顶级评论的rootId是自己）
     */
    private Long rootId;

    /**
     * 被回复用户ID
     */
    private Long replyToUserId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论图片文件ID数组（UUIDv7格式）
     */
    private String[] imageIds;

    /**
     * 评论语音文件ID（UUIDv7格式）
     */
    private String voiceId;

    /**
     * 语音时长（秒）
     */
    private Integer voiceDuration;

    /**
     * 评论状态
     */
    private CommentStatus status;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 评论统计
     */
    private CommentStats stats;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数（创建新评论）
     */
    private Comment(Long id, Long postId, Long authorId, String content) {
        Assert.notNull(id, "评论ID不能为空");
        Assert.isTrue(id > 0, "评论ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.isTrue(postId > 0, "文章ID必须为正数");
        Assert.notNull(authorId, "作者ID不能为空");
        Assert.isTrue(authorId > 0, "作者ID必须为正数");
        Assert.hasText(content, "评论内容不能为空");

        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.status = CommentStatus.NORMAL;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.stats = CommentStats.empty();
    }

    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Comment(
            @JsonProperty("id") Long id,
            @JsonProperty("postId") Long postId,
            @JsonProperty("authorId") Long authorId,
            @JsonProperty("content") String content,
            @JsonProperty("imageIds") String[] imageIds,
            @JsonProperty("voiceId") String voiceId,
            @JsonProperty("voiceDuration") Integer voiceDuration,
            @JsonProperty("parentId") Long parentId,
            @JsonProperty("rootId") Long rootId,
            @JsonProperty("replyToUserId") Long replyToUserId,
            @JsonProperty("status") CommentStatus status,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("updatedAt") LocalDateTime updatedAt,
            @JsonProperty("stats") CommentStats stats) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.imageIds = imageIds;
        this.voiceId = voiceId;
        this.voiceDuration = voiceDuration;
        this.parentId = parentId;
        this.rootId = rootId;
        this.replyToUserId = replyToUserId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.stats = stats != null ? stats : CommentStats.empty();
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建顶级评论
     *
     * @param id 评论ID
     * @param postId 文章ID
     * @param authorId 作者ID
     * @param content 评论内容
     * @param imageIds 图片文件ID数组
     * @param voiceId 语音文件ID
     * @param voiceDuration 语音时长（秒）
     * @return 评论实例
     */
    public static Comment createTopLevel(Long id, Long postId, Long authorId, String content, 
                                        String[] imageIds, String voiceId, Integer voiceDuration) {
        validateContent(content, imageIds, voiceId);
        Comment comment = new Comment(id, postId, authorId, content);
        comment.rootId = id;  // 顶级评论的rootId是自己
        comment.imageIds = imageIds;
        comment.voiceId = voiceId;
        comment.voiceDuration = voiceDuration;
        return comment;
    }

    /**
     * 创建回复评论（扁平化：所有回复的parentId都指向顶级评论）
     *
     * @param id 评论ID
     * @param postId 文章ID
     * @param authorId 作者ID
     * @param content 评论内容
     * @param imageIds 图片文件ID数组
     * @param voiceId 语音文件ID
     * @param voiceDuration 语音时长（秒）
     * @param rootId 根评论ID
     * @param replyToUserId 被回复用户ID
     * @return 评论实例
     */
    public static Comment createReply(Long id, Long postId, Long authorId, String content,
                                      String[] imageIds, String voiceId, Integer voiceDuration,
                                      Long rootId, Long replyToUserId) {
        validateContent(content, imageIds, voiceId);
        Comment comment = new Comment(id, postId, authorId, content);
        comment.parentId = rootId;      // parentId 指向顶级评论
        comment.rootId = rootId;        // rootId 也指向顶级评论
        comment.replyToUserId = replyToUserId;
        comment.imageIds = imageIds;
        comment.voiceId = voiceId;
        comment.voiceDuration = voiceDuration;
        return comment;
    }

    /**
     * 从持久化恢复评论
     */
    public static Comment reconstitute(Long id, Long postId, Long authorId, String content,
                                       String[] imageIds, String voiceId, Integer voiceDuration,
                                       Long parentId, Long rootId, Long replyToUserId,
                                       CommentStatus status, LocalDateTime createdAt,
                                       LocalDateTime updatedAt, CommentStats stats) {
        return new Comment(id, postId, authorId, content, imageIds, voiceId, voiceDuration,
                parentId, rootId, replyToUserId, status, createdAt, updatedAt, stats);
    }

    // ==================== 领域行为 ====================

    /**
     * 编辑评论
     *
     * @param newContent 新内容
     * @param operatorId 操作者ID
     */
    public void edit(String newContent, Long operatorId) {
        if (!this.authorId.equals(operatorId)) {
            throw new DomainException("只能编辑自己的评论");
        }
        if (this.status == CommentStatus.DELETED) {
            throw new DomainException("已删除的评论不能编辑");
        }
        validateContent(newContent, null, null);

        this.content = newContent;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 删除评论
     *
     * @param operatorId 操作者ID
     * @param isAdmin 是否为管理员
     */
    public void delete(Long operatorId, boolean isAdmin) {
        if (!isAdmin && !this.authorId.equals(operatorId)) {
            throw new DomainException("无权删除此评论");
        }
        if (this.status == CommentStatus.DELETED) {
            throw new DomainException("评论已经删除");
        }

        this.status = CommentStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== 查询方法 ====================

    /**
     * 是否为顶级评论
     */
    public boolean isTopLevel() {
        return this.parentId == null;
    }

    /**
     * 是否为回复
     */
    public boolean isReply() {
        return this.parentId != null;
    }

    /**
     * 是否为指定用户所有
     */
    public boolean isOwnedBy(Long userId) {
        return this.authorId.equals(userId);
    }

    /**
     * 是否已删除
     */
    public boolean isDeleted() {
        return this.status == CommentStatus.DELETED;
    }

    // ==================== 私有方法 ====================

    /**
     * 验证评论内容
     * 评论必须包含文本、图片或语音中的至少一项
     */
    private static void validateContent(String content, String[] imageIds, String voiceId) {
        boolean hasText = StringUtils.hasText(content);
        boolean hasImages = imageIds != null && imageIds.length > 0;
        boolean hasVoice = StringUtils.hasText(voiceId);

        if (!hasText && !hasImages && !hasVoice) {
            throw new DomainException("评论内容、图片、语音至少需要一项");
        }

        if (hasText && content.length() > 2000) {
            throw new DomainException("评论内容不能超过2000字");
        }

        if (hasImages && imageIds.length > 9) {
            throw new DomainException("评论图片不能超过9张");
        }

        if (hasVoice && voiceId != null) {
            // 语音评论不能同时包含图片
            if (hasImages) {
                throw new DomainException("语音评论不能同时包含图片");
            }
        }
    }
}
