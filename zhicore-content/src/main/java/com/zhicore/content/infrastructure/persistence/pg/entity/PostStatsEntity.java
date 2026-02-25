package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章统计信息数据库实体
 * 
 * 对应 post_stats 表，存储文章的统计数据（浏览量、点赞数、评论数、分享数）。
 * 通过消息队列异步更新，实现最终一致性。
 * 
 * @author ZhiCore Team
 */
@Data
@TableName("post_stats")
public class PostStatsEntity {
    
    /**
     * 文章ID（主键）
     */
    @TableId
    private Long postId;
    
    /**
     * 浏览量
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
     * 最后更新时间
     */
    private LocalDateTime lastUpdatedAt;
}
