package com.zhicore.content.application.port.repo;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostStats 仓储端口接口
 * 
 * 定义文章统计信息的持久化操作契约，由基础设施层实现。
 * PostStats 是独立的模型，通过消息队列异步更新，实现最终一致性。
 * 
 * @author ZhiCore Team
 */
public interface PostStatsRepository {
    
    /**
     * 根据文章ID查找统计信息
     * 
     * @param postId 文章ID（值对象）
     * @return 统计信息（可能为空）
     */
    Optional<PostStats> findById(PostId postId);
    
    /**
     * 批量查询统计信息（避免 N+1 查询）
     * 
     * @param postIds 文章ID列表（值对象）
     * @return 文章ID到统计信息的映射
     */
    Map<PostId, PostStats> findByIds(List<PostId> postIds);
    
    /**
     * 插入或更新统计信息（覆盖式更新）
     * 
     * 使用 upsert 语义，如果记录存在则更新，不存在则插入。
     * 采用覆盖式更新策略，直接使用新值替换旧值。
     * 
     * @param postId 文章ID（值对象）
     * @param stats 统计信息
     */
    void upsert(PostId postId, PostStats stats);
}

