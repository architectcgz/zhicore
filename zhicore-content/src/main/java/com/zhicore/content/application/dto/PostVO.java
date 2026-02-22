package com.zhicore.content.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章视图对象
 *
 * @author ZhiCore Team
 */
@Data
public class PostVO {

    private Long id;

    private Long ownerId;

    private String ownerName;

    private String ownerAvatar;

    private String title;

    private String raw;

    private String html;

    private String excerpt;

    private String coverImageUrl;

    private String status;

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

    private long viewCount;

    // 当前用户状态
    private boolean liked;

    private boolean favorited;
}
