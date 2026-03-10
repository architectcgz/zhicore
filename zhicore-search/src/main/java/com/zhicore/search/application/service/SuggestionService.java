package com.zhicore.search.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.search.application.sentinel.SearchSentinelHandlers;
import com.zhicore.search.application.sentinel.SearchSentinelResources;
import com.zhicore.search.application.port.store.SuggestionCacheStore;
import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private final SuggestionCacheStore suggestionCacheStore;

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
    @SentinelResource(
            value = SearchSentinelResources.GET_SUGGESTIONS,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleSuggestionsBlocked"
    )
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
        suggestionCacheStore.incrementHotKeywordScore(normalizedKeyword);

        // 保持热门搜索词数量限制
        long size = suggestionCacheStore.getHotKeywordCount();
        if (size > MAX_HOT_KEYWORDS * 2L) {
            // 移除分数最低的词
            suggestionCacheStore.removeHotKeywordRange(0, size - MAX_HOT_KEYWORDS - 1);
        }

        // 更新用户搜索历史
        if (userId != null) {
            suggestionCacheStore.recordUserHistory(userId, normalizedKeyword, MAX_HISTORY_SIZE, Duration.ofDays(30));
        }

        log.debug("Recorded search: keyword={}, userId={}", keyword, userId);
    }

    /**
     * 获取热门搜索词
     *
     * @param limit 限制数量
     * @return 热门搜索词列表
     */
    @SentinelResource(
            value = SearchSentinelResources.GET_HOT_KEYWORDS,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleHotKeywordsBlocked"
    )
    public List<String> getHotKeywords(int limit) {
        return suggestionCacheStore.getHotKeywords(limit);
    }

    /**
     * 获取用户搜索历史
     *
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 搜索历史列表
     */
    @SentinelResource(
            value = SearchSentinelResources.GET_USER_HISTORY,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleUserHistoryBlocked"
    )
    public List<String> getUserHistory(String userId, int limit) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return suggestionCacheStore.getUserHistory(userId, limit);
    }

    /**
     * 清除用户搜索历史
     *
     * @param userId 用户ID
     */
    public void clearUserHistory(String userId) {
        if (userId != null) {
            suggestionCacheStore.clearUserHistory(userId);
            log.info("Cleared search history for user: {}", userId);
        }
    }

    private List<String> getHotKeywordsMatching(String prefix, int limit) {
        String normalizedPrefix = prefix.toLowerCase();
        return suggestionCacheStore.getHotKeywords(MAX_HOT_KEYWORDS).stream()
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
