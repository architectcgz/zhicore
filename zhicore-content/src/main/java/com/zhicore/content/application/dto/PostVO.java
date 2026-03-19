package com.zhicore.content.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章视图对象
 *
 * @author ZhiCore Team
 */
@Data
public class PostVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private String ownerName;

    private String ownerAvatar;

    private String title;

    private String raw;

    private String html;

    private String excerpt;

    private String coverImageUrl;

    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long topicId;

    private String topicName;

    private LocalDateTime publishedAt;

    private LocalDateTime scheduledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 统计数据
    private int likeCount;

    private int commentCount;

    private int favoriteCount;

    private int viewCount;

    // 当前用户状态
    private boolean liked;

    private boolean favorited;

    // ===== 兼容新查询层命名 =====
    public void setAuthorId(Long authorId) { this.ownerId = authorId; }
    public void setAuthorName(String authorName) { this.ownerName = authorName; }
    public void setAuthorAvatar(String authorAvatar) { this.ownerAvatar = authorAvatar; }
    public void setCoverImage(String coverImage) { this.coverImageUrl = coverImage; }
    public void setContent(String content) { this.raw = content; }
    public void setStatus(String status) { this.status = status; }
    public void setStatus(com.zhicore.content.domain.model.PostStatus status) { this.status = status != null ? status.name() : null; }
    public void setScheduledPublishAt(LocalDateTime scheduledPublishAt) { this.scheduledAt = scheduledPublishAt; }
    public void setContentDegraded(boolean contentDegraded) { }
}
