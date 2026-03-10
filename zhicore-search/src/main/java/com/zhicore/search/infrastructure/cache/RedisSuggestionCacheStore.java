package com.zhicore.search.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.search.application.port.store.SuggestionCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的搜索建议缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisSuggestionCacheStore implements SuggestionCacheStore {

    private static final String HOT_KEY = CacheConstants.withNamespace("search") + ":hot:keywords";
    private static final String HISTORY_KEY_PREFIX = CacheConstants.withNamespace("search") + ":history:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getHotKeywords(int limit) {
        Set<String> keywords = redisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, Math.max(0, limit - 1));
        return keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
    }

    @Override
    public void incrementHotKeywordScore(String keyword) {
        redisTemplate.opsForZSet().incrementScore(HOT_KEY, keyword, 1);
    }

    @Override
    public long getHotKeywordCount() {
        Long size = redisTemplate.opsForZSet().size(HOT_KEY);
        return size != null ? size : 0L;
    }

    @Override
    public void removeHotKeywordRange(long start, long end) {
        redisTemplate.opsForZSet().removeRange(HOT_KEY, start, end);
    }

    @Override
    public List<String> getUserHistory(String userId, int limit) {
        List<String> history = redisTemplate.opsForList().range(historyKey(userId), 0, Math.max(0, limit - 1));
        return history != null ? history : new ArrayList<>();
    }

    @Override
    public void recordUserHistory(String userId, String keyword, int maxHistorySize, Duration ttl) {
        String historyKey = historyKey(userId);
        redisTemplate.opsForList().remove(historyKey, 0, keyword);
        redisTemplate.opsForList().leftPush(historyKey, keyword);
        redisTemplate.opsForList().trim(historyKey, 0, Math.max(0, maxHistorySize - 1));
        redisTemplate.expire(historyKey, ttl);
    }

    @Override
    public void clearUserHistory(String userId) {
        redisTemplate.delete(historyKey(userId));
    }

    private String historyKey(String userId) {
        return HISTORY_KEY_PREFIX + userId;
    }
}
