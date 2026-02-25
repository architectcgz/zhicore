package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostStatsEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostStatsEntityMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostStatsMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PostStats 仓储实现
 * 
 * 实现 PostStatsRepository 端口接口，提供基于 PostgreSQL 的文章统计信息持久化。
 * PostStats 是独立的模型，通过消息队列异步更新，实现最终一致性。
 * 使用覆盖式更新策略（upsert），直接使用新值替换旧值。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostStatsRepositoryImpl implements PostStatsRepository {
    
    private final PostStatsMyBatisMapper postStatsMapper;
    private final PostStatsEntityMapper entityMapper;
    
    @Override
    public Optional<PostStats> findById(PostId postId) {
        try {
            // 值对象转 Long 用于数据库查询
            PostStatsEntity entity = postStatsMapper.selectById(postId.getValue());
            
            if (entity == null) {
                return Optional.empty();
            }
            
            PostStats stats = entityMapper.toDomain(entity);
            return Optional.of(stats);
            
        } catch (Exception e) {
            log.error("Failed to find post stats: postId={}", postId.getValue(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public Map<PostId, PostStats> findByIds(List<PostId> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        
        try {
            // 值对象转 Long 列表用于批量查询
            List<Long> postIdValues = postIds.stream()
                .map(PostId::getValue)
                .collect(Collectors.toList());
            
            // 批量查询，避免 N+1 问题
            List<PostStatsEntity> entities = postStatsMapper.selectBatchIds(postIdValues);
            
            // 转换为 Map<PostId, PostStats>
            return entities.stream()
                .collect(Collectors.toMap(
                    entity -> PostId.of(entity.getPostId()),  // Long 转值对象
                    entityMapper::toDomain
                ));
                
        } catch (Exception e) {
            log.error("Failed to find post stats by ids: postIds={}", 
                postIds.stream().map(PostId::getValue).collect(Collectors.toList()), e);
            return Map.of();
        }
    }
    
    @Override
    public void upsert(PostId postId, PostStats stats) {
        try {
            PostStatsEntity entity = entityMapper.toEntity(stats);
            // 值对象转 Long
            entity.setPostId(postId.getValue());
            
            // 检查是否存在
            PostStatsEntity existing = postStatsMapper.selectById(postId.getValue());
            
            if (existing == null) {
                // 插入新记录
                postStatsMapper.insert(entity);
                log.debug("Inserted post stats: postId={}", postId.getValue());
            } else {
                // 覆盖式更新（使用新值替换所有字段）
                postStatsMapper.updateById(entity);
                log.debug("Updated post stats: postId={}", postId.getValue());
            }
            
        } catch (Exception e) {
            log.error("Failed to upsert post stats: postId={}, stats={}", postId.getValue(), stats, e);
            throw new RuntimeException("Failed to upsert post stats", e);
        }
    }
}
