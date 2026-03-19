package com.zhicore.content.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章简要视图对象（用于列表）
 *
 * @author ZhiCore Team
 */
@Data
public class PostBriefVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private String ownerName;

    private String ownerAvatar;

    private String title;

    private String excerpt;

    private String coverImageUrl;

    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

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
}
