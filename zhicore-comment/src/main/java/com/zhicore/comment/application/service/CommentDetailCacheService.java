package com.zhicore.comment.application.service;

import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 评论详情读取缓存服务
 *
 * 负责评论详情读取路径上的缓存、热点锁与降级处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentDetailCacheService {

    private static final String ENTITY_TYPE_COMMENT = "comment";

    private static final class DelegateQueryException extends RuntimeException {
        private DelegateQueryException(RuntimeException cause) {
            super(cause);
        }
    }

    private final CommentRepository commentRepository;
    private final CommentDetailCacheStore cacheStore;
    private final LockManager lockManager;
    private final CacheProperties cacheProperties;
    private final HotDataIdentifier hotDataIdentifier;
    private final CommentCacheKeyResolver commentCacheKeyResolver;

    /**
     * 读取评论详情（缓存优先）
     */
    public Optional<Comment> findById(Long id) {
        try {
            Optional<Comment> cachedResult = toOptional(cacheStore.get(id));
            if (cachedResult != null) {
                return cachedResult;
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed, falling back to database: commentId={}, error={}",
                    id, e.getMessage());
            return commentRepository.findById(id);
        } catch (Exception e) {
            log.error("Cache read failed, falling back to database: commentId={}, error={}",
                    id, e.getMessage(), e);
            return commentRepository.findById(id);
        }

        try {
            hotDataIdentifier.recordAccess(ENTITY_TYPE_COMMENT, id);
        } catch (Exception e) {
            log.warn("Failed to record access for hot data identification: commentId={}, error={}",
                    id, e.getMessage());
        }

        boolean isHotData = false;
        try {
            isHotData = hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, id);
        } catch (Exception e) {
            log.warn("Failed to check hot data status: commentId={}, error={}", id, e.getMessage());
        }

        if (!isHotData) {
            return loadAndCacheComment(id);
        }

        return loadCommentWithLock(id);
    }

    /**
     * 获取锁等待队列长度（Redisson API 不支持时返回 -1）
     */
    public int getLockQueueLength(String lockKey) {
        log.debug("Lock queue length monitoring not supported for key: {}", lockKey);
        return -1;
    }

    private Optional<Comment> loadAndCacheComment(Long commentId) {
        Optional<Comment> result = commentRepository.findById(commentId);

        try {
            if (result.isPresent()) {
                cacheStore.set(commentId, result.get(), entityDetailTtl());
            } else {
                cacheStore.setNull(commentId, nullValueTtl());
            }
        } catch (Exception e) {
            log.warn("Cache write failed for comment {}: {}", commentId, e.getMessage());
        }

        return result;
    }

    private Optional<Comment> loadCommentWithLock(Long commentId) {
        boolean fair = cacheProperties.getLock().isFair();
        String lockKey = commentCacheKeyResolver.lockDetail(commentId);
        Duration waitTime = Duration.ofSeconds(cacheProperties.getLock().getWaitTime());
        Duration leaseTime = Duration.ofSeconds(cacheProperties.getLock().getLeaseTime());

        try {
            boolean acquired = lockManager.tryLock(lockKey, waitTime, leaseTime, fair);

            if (!acquired) {
                log.warn("Failed to acquire lock for comment {} within timeout, falling back to database", commentId);
                return commentRepository.findById(commentId);
            }

            try {
                Optional<Comment> cachedResult = toOptional(cacheStore.get(commentId));
                if (cachedResult != null) {
                    return cachedResult;
                }

                Optional<Comment> result;
                try {
                    result = commentRepository.findById(commentId);
                } catch (RuntimeException e) {
                    throw new DelegateQueryException(e);
                }

                try {
                    if (result.isPresent()) {
                        cacheStore.set(commentId, result.get(), entityDetailTtl());
                    } else {
                        cacheStore.setNull(commentId, nullValueTtl());
                    }
                } catch (RedisConnectionFailureException e) {
                    log.error("Redis connection failed during cache write: commentId={}, error={}",
                            commentId, e.getMessage());
                } catch (Exception e) {
                    log.warn("Failed to cache comment after database query: commentId={}, error={}",
                            commentId, e.getMessage());
                }

                return result;
            } finally {
                if (lockManager.isHeldByCurrentThread(lockKey, fair)) {
                    try {
                        lockManager.unlock(lockKey, fair);
                    } catch (Exception e) {
                        log.error("Failed to release lock for comment {}, will rely on auto-expiration: {}",
                                commentId, e.getMessage());
                    }
                }
            }
        } catch (RedisException e) {
            log.error("Redisson connection failed for commentId={}, falling back to database: {}",
                    commentId, e.getMessage());
            return commentRepository.findById(commentId);
        } catch (DelegateQueryException e) {
            throw (RuntimeException) e.getCause();
        } catch (Exception e) {
            log.error("Unexpected error during cache operation for comment {}: {}", commentId, e.getMessage(), e);
            return commentRepository.findById(commentId);
        }
    }

    private Optional<Comment> toOptional(CacheResult<Comment> result) {
        if (result == null || result.isMiss()) {
            return null;
        }
        if (result.isNull()) {
            return Optional.empty();
        }
        return Optional.ofNullable(result.getValue());
    }

    private Duration entityDetailTtl() {
        long ttl = cacheProperties.getTtl().getEntityDetail();
        ttl = ttl + (long) (Math.random() * ttl * 0.1);
        return Duration.ofSeconds(ttl);
    }

    private Duration nullValueTtl() {
        return Duration.ofSeconds(cacheProperties.getTtl().getNullValue());
    }
}
