package com.zhicore.content.application.query.view;

import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 文章详情视图
 * 
 * 用于查询服务返回完整的文章信息
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class PostDetailView {
    
    /**
     * 文章 ID
     */
    private PostId id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 摘要
     */
    private String excerpt;
    
    /**
     * 封面图片
     */
    private String coverImage;
    
    /**
     * 文章内容（Markdown 或 HTML）
     */
    private String content;
    
    /**
     * 内容是否降级（MongoDB 不可用时为 true）
     */
    private boolean contentDegraded;
    
    /**
     * 文章状态
     */
    private PostStatus status;
    
    /**
     * 作者快照
     */
    private OwnerSnapshot ownerSnapshot;
    
    /**
     * 标签 ID 集合（值对象）
     */
    private Set<TagId> tagIds;
    
    /**
     * 话题 ID（值对象）
     */
    private TopicId topicId;
    
    /**
     * 浏览数
     */
    private int viewCount;
    
    /**
     * 点赞数
     */
    private int likeCount;
    
    /**
     * 评论数
     */
    private int commentCount;
    
    /**
     * 分享数
     */
    private int shareCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 定时发布时间（如果状态为 SCHEDULED）
     */
    private LocalDateTime scheduledPublishAt;

    /**
     * 发布时间（未发布时为 null）
     */
    private LocalDateTime publishedAt;
}

