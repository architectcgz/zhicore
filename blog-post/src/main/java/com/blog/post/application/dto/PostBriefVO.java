package com.blog.post.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章简要视图对象（用于列表）
 *
 * @author Blog Team
 */
@Data
public class PostBriefVO {

    private Long id;

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

    private long viewCount;

    // 当前用户状态
    private boolean liked;

    private boolean favorited;
}
