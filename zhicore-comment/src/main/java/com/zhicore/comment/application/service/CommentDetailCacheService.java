package com.zhicore.comment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final HotDataIdentifier hotDataIdentifier;
    private final ObjectMapper objectMapper;

    /**
     * 读取评论详情（缓存优先）
     */
    public Optional<Comment> findById(Long id) {
        String cacheKey = CommentRedisKeys.detail(id);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (CacheConstants.isNullMarker(cached)) {
                    return Optional.empty();
                }
                Comment comment = objectMapper.convertValue(cached, Comment.class);
                return Optional.of(comment);
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
            return loadAndCacheComment(id, cacheKey);
        }

        return loadCommentWithLock(id, cacheKey);
    }

    /**
     * 获取锁等待队列长度（Redisson API 不支持时返回 -1）
     */
    public int getLockQueueLength(String lockKey) {
        log.debug("Lock queue length monitoring not supported for key: {}", lockKey);
        return -1;
    }

    private RLock getLock(String lockKey) {
        if (cacheProperties.getLock().isFair()) {
            log.debug("Using fair lock for key: {}", lockKey);
            return redissonClient.getFairLock(lockKey);
        } else {
            log.debug("Using non-fair lock for key: {}", lockKey);
            return redissonClient.getLock(lockKey);
        }
    }

    private Optional<Comment> loadAndCacheComment(Long commentId, String cacheKey) {
        Optional<Comment> result = commentRepository.findById(commentId);

        try {
            if (result.isPresent()) {
                long ttl = cacheProperties.getTtl().getEntityDetail();
                ttl = ttl + (long) (Math.random() * ttl * 0.1);
                redisTemplate.opsForValue().set(cacheKey, result.get(), ttl, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        CacheConstants.NULL_MARKER,
                        cacheProperties.getTtl().getNullValue(),
                        TimeUnit.SECONDS
                );
            }
        } catch (Exception e) {
            log.warn("Cache write failed for comment {}: {}", commentId, e.getMessage());
        }

        return result;
    }

    private Optional<Comment> loadCommentWithLock(Long commentId, String cacheKey) {
        String lockKey = CommentRedisKeys.lockDetail(commentId);
        RLock lock = getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(
                    cacheProperties.getLock().getWaitTime(),
                    cacheProperties.getLock().getLeaseTime(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                log.warn("Failed to acquire lock for comment {} within timeout, falling back to database", commentId);
                return commentRepository.findById(commentId);
            }

            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CacheConstants.isNullMarker(cached)) {
                        return Optional.empty();
                    }
                    Comment comment = objectMapper.convertValue(cached, Comment.class);
                    return Optional.of(comment);
                }

                Optional<Comment> result;
                try {
                    result = commentRepository.findById(commentId);
                } catch (RuntimeException e) {
                    throw new DelegateQueryException(e);
                }

                try {
                    if (result.isPresent()) {
                        long ttl = cacheProperties.getTtl().getEntityDetail();
                        ttl = ttl + (long) (Math.random() * ttl * 0.1);
                        redisTemplate.opsForValue().set(cacheKey, result.get(), ttl, TimeUnit.SECONDS);
                    } else {
                        redisTemplate.opsForValue().set(
                                cacheKey,
                                CacheConstants.NULL_MARKER,
                                cacheProperties.getTtl().getNullValue(),
                                TimeUnit.SECONDS
                        );
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
                if (lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                    } catch (Exception e) {
                        log.error("Failed to release lock for comment {}, will rely on auto-expiration: {}",
                                commentId, e.getMessage());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for comment {}, falling back to database", commentId);
            return commentRepository.findById(commentId);
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
}
