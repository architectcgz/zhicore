package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.cdc.store.CommentLikesCacheStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisCommentLikesCacheStore implements CommentLikesCacheStore {

    private static final String COMMENT_LIKES_SET_PREFIX = "comment:likes:";
    private static final String COMMENT_LIKE_COUNT_PREFIX = "comment:stats:";

    private final RedissonClient redissonClient;

    @Override
    public void addLike(String commentId, String userId) {
        RSet<String> likeSet = redissonClient.getSet(COMMENT_LIKES_SET_PREFIX + commentId);
        likeSet.add(userId);
        redissonClient.getAtomicLong(COMMENT_LIKE_COUNT_PREFIX + commentId + ":like_count").incrementAndGet();
    }

    @Override
    public void removeLike(String commentId, String userId) {
        RSet<String> likeSet = redissonClient.getSet(COMMENT_LIKES_SET_PREFIX + commentId);
        likeSet.remove(userId);
        RAtomicLong counter = redissonClient.getAtomicLong(COMMENT_LIKE_COUNT_PREFIX + commentId + ":like_count");
        long newCount = counter.decrementAndGet();
        if (newCount < 0) {
            counter.set(0);
        }
    }
}
