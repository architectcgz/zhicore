package com.zhicore.search.application.service;

import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索建议服务
 * 
 * 提供基于前缀匹配的搜索建议功能，支持热门搜索词缓存
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final PostSearchRepository postSearchRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String HOT_SEARCH_KEY = "search:hot:keywords";
    private static final String SEARCH_HISTORY_KEY_PREFIX = "search:history:";
    private static final int MAX_HOT_KEYWORDS = 20;
    private static final int MAX_HISTORY_SIZE = 10;

    /**
     * 获取搜索建议
     * 
     * 优先返回热门搜索词，然后是用户历史搜索，最后是 ES 前缀匹配
     *
     * @param prefix 前缀
     * @param userId 用户ID（可选）
     * @param limit 限制数量
     * @return 建议列表
     */
    public List<String> getSuggestions(String prefix, String userId, int limit) {
        List<String> suggestions = new ArrayList<>();

        // 1. 从热门搜索词中匹配
        List<String> hotMatches = getHotKeywordsMatching(prefix, limit);
        suggestions.addAll(hotMatches);

        // 2. 从用户历史搜索中匹配
        if (userId != null && suggestions.size() < limit) {
            List<String> historyMatches = getUserHistoryMatching(userId, prefix, limit - suggestions.size());
            for (String match : historyMatches) {
                if (!suggestions.contains(match)) {
                    suggestions.add(match);
                }
            }
        }

        // 3. 从 ES 前缀匹配
        if (suggestions.size() < limit) {
            List<String> esMatches = postSearchRepository.suggest(prefix, limit - suggestions.size());
            for (String match : esMatches) {
                if (!suggestions.contains(match)) {
                    suggestions.add(match);
                }
            }
        }

        return suggestions.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 记录搜索关键词
     * 
     * 更新热门搜索词和用户搜索历史
     *
     * @param keyword 关键词
     * @param userId 用户ID（可选）
     */
    public void recordSearch(String keyword, String userId) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String normalizedKeyword = keyword.trim().toLowerCase();

        // 更新热门搜索词
        redisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, normalizedKeyword, 1);

        // 保持热门搜索词数量限制
        Long size = redisTemplate.opsForZSet().size(HOT_SEARCH_KEY);
        if (size != null && size > MAX_HOT_KEYWORDS * 2) {
            // 移除分数最低的词
            redisTemplate.opsForZSet().removeRange(HOT_SEARCH_KEY, 0, size - MAX_HOT_KEYWORDS - 1);
        }

        // 更新用户搜索历史
        if (userId != null) {
            String historyKey = SEARCH_HISTORY_KEY_PREFIX + userId;
            redisTemplate.opsForList().remove(historyKey, 0, normalizedKeyword);
            redisTemplate.opsForList().leftPush(historyKey, normalizedKeyword);
            redisTemplate.opsForList().trim(historyKey, 0, MAX_HISTORY_SIZE - 1);
            redisTemplate.expire(historyKey, Duration.ofDays(30));
        }

        log.debug("Recorded search: keyword={}, userId={}", keyword, userId);
    }

    /**
     * 获取热门搜索词
     *
     * @param limit 限制数量
     * @return 热门搜索词列表
     */
    public List<String> getHotKeywords(int limit) {
        Set<String> keywords = redisTemplate.opsForZSet()
            .reverseRange(HOT_SEARCH_KEY, 0, limit - 1);
        return keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
    }

    /**
     * 获取用户搜索历史
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 搜索历史列表
     */
    public List<String> getUserHistory(String userId, int limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        String historyKey = SEARCH_HISTORY_KEY_PREFIX + userId;
        List<String> history = redisTemplate.opsForList().range(historyKey, 0, limit - 1);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * 清除用户搜索历史
     *
     * @param userId 用户ID
     */
    public void clearUserHistory(String userId) {
        if (userId != null) {
            String historyKey = SEARCH_HISTORY_KEY_PREFIX + userId;
            redisTemplate.delete(historyKey);
            log.info("Cleared search history for user: {}", userId);
        }
    }

    private List<String> getHotKeywordsMatching(String prefix, int limit) {
        Set<String> allHot = redisTemplate.opsForZSet()
            .reverseRange(HOT_SEARCH_KEY, 0, MAX_HOT_KEYWORDS - 1);
        
        if (allHot == null) {
            return new ArrayList<>();
        }

        String normalizedPrefix = prefix.toLowerCase();
        return allHot.stream()
            .filter(keyword -> keyword.startsWith(normalizedPrefix))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private List<String> getUserHistoryMatching(String userId, String prefix, int limit) {
        List<String> history = getUserHistory(userId, MAX_HISTORY_SIZE);
        String normalizedPrefix = prefix.toLowerCase();
        
        return history.stream()
            .filter(keyword -> keyword.startsWith(normalizedPrefix))
            .limit(limit)
            .collect(Collectors.toList());
    }
}
