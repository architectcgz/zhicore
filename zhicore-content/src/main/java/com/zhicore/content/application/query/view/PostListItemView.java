package com.zhicore.content.application.query.view;

import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.PostId;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章列表项视图
 * 
 * 用于列表查询返回简要的文章信息
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class PostListItemView {
    
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
     * 作者快照
     */
    private OwnerSnapshot ownerSnapshot;
    
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
}
