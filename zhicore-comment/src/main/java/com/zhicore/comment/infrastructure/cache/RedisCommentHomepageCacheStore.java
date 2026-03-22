package com.zhicore.comment.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.port.store.CommentHomepageCacheStore;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的首页评论快照缓存。
 */
@Component
@RequiredArgsConstructor
public class RedisCommentHomepageCacheStore implements CommentHomepageCacheStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<PageResult<CommentVO>> get(Long postId, CommentSortType sortType, int size, int hotRepliesLimit) {
        Object cached = redisTemplate.opsForValue().get(CommentRedisKeys.homepageSnapshot(postId, sortType, size, hotRepliesLimit));
        if (cached == null) {
            return Optional.empty();
        }
        if (cached instanceof PageResult<?> pageResult && isTypedCommentSnapshot(pageResult.getRecords())) {
            return Optional.of(castPageResult(pageResult));
        }
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(PageResult.class, CommentVO.class);
        return Optional.of(objectMapper.convertValue(cached, javaType));
    }

    @Override
    public void set(Long postId,
                    CommentSortType sortType,
                    int size,
                    int hotRepliesLimit,
                    PageResult<CommentVO> snapshot,
                    Duration ttl) {
        redisTemplate.opsForValue().set(
                CommentRedisKeys.homepageSnapshot(postId, sortType, size, hotRepliesLimit),
                snapshot,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    private boolean isTypedCommentSnapshot(List<?> records) {
        if (records == null || records.isEmpty()) {
            return true;
        }
        return records.stream()
                .filter(Objects::nonNull)
                .allMatch(CommentVO.class::isInstance);
    }

    @SuppressWarnings("unchecked")
    private PageResult<CommentVO> castPageResult(PageResult<?> pageResult) {
        return (PageResult<CommentVO>) pageResult;
    }
}
