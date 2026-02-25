package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostStatsEntity;
import org.springframework.stereotype.Component;

/**
 * PostStats 实体映射器
 * 
 * 负责在领域模型（PostStats）和数据库实体（PostStatsEntity）之间进行转换。
 * 
 * @author ZhiCore Team
 */
@Component
public class PostStatsEntityMapper {
    
    /**
     * 将数据库实体转换为领域模型
     * 
     * @param entity 数据库实体
     * @return 领域模型
     */
    public PostStats toDomain(PostStatsEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return new PostStats(
            PostId.of(entity.getPostId()),
            entity.getViewCount(),
            entity.getLikeCount(),
            entity.getCommentCount(),
            entity.getShareCount(),
            entity.getLastUpdatedAt()
        );
    }
    
    /**
     * 将领域模型转换为数据库实体
     * 
     * @param stats 领域模型
     * @return 数据库实体
     */
    public PostStatsEntity toEntity(PostStats stats) {
        if (stats == null) {
            return null;
        }
        
        PostStatsEntity entity = new PostStatsEntity();
        entity.setPostId(stats.getPostId().getValue());
        entity.setViewCount(stats.getViewCount());
        entity.setLikeCount(stats.getLikeCount());
        entity.setCommentCount(stats.getCommentCount());
        entity.setShareCount(stats.getShareCount());
        entity.setLastUpdatedAt(stats.getLastUpdatedAt());
        
        return entity;
    }
}
