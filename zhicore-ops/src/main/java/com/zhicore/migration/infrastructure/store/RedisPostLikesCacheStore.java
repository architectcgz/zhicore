package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.cdc.store.PostLikesCacheStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPostLikesCacheStore implements PostLikesCacheStore {

    private static final String POST_LIKES_SET_PREFIX = "post:likes:";
    private static final String POST_LIKE_COUNT_PREFIX = "post:stats:";

    private final RedissonClient redissonClient;

    @Override
    public void addLike(String postId, String userId) {
        RSet<String> likeSet = redissonClient.getSet(POST_LIKES_SET_PREFIX + postId);
        likeSet.add(userId);
        redissonClient.getAtomicLong(POST_LIKE_COUNT_PREFIX + postId + ":like_count").incrementAndGet();
    }

    @Override
    public void removeLike(String postId, String userId) {
        RSet<String> likeSet = redissonClient.getSet(POST_LIKES_SET_PREFIX + postId);
        likeSet.remove(userId);
        RAtomicLong counter = redissonClient.getAtomicLong(POST_LIKE_COUNT_PREFIX + postId + ":like_count");
        long newCount = counter.decrementAndGet();
        if (newCount < 0) {
            counter.set(0);
        }
    }
}
