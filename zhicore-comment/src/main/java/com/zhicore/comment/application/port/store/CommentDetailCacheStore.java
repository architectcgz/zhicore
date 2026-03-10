package com.zhicore.comment.application.port.store;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.common.cache.port.CacheResult;

import java.time.Duration;

/**
 * 评论详情缓存存储端口。
 *
 * 封装评论详情的缓存三态读写，避免应用层直接依赖 RedisTemplate。
 */
public interface CommentDetailCacheStore {

    /**
     * 读取评论详情缓存。
     *
     * @param commentId 评论ID
     * @return 缓存三态结果
     */
    CacheResult<Comment> get(Long commentId);

    /**
     * 写入评论详情缓存。
     *
     * @param commentId 评论ID
     * @param comment 评论详情
     * @param ttl 过期时间
     */
    void set(Long commentId, Comment comment, Duration ttl);

    /**
     * 写入空值缓存。
     *
     * @param commentId 评论ID
     * @param ttl 过期时间
     */
    void setNull(Long commentId, Duration ttl);

    /**
     * 删除评论详情缓存。
     *
     * @param commentId 评论ID
     */
    void evict(Long commentId);
}
