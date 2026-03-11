package com.zhicore.search.application.service;

import com.zhicore.search.application.port.store.SuggestionCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 搜索建议写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionCommandService {

    private static final int MAX_HOT_KEYWORDS = 20;
    private static final int MAX_HISTORY_SIZE = 10;

    private final SuggestionCacheStore suggestionCacheStore;

    public void recordSearch(String keyword, String userId) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String normalizedKeyword = keyword.trim().toLowerCase();
        suggestionCacheStore.incrementHotKeywordScore(normalizedKeyword);

        long size = suggestionCacheStore.getHotKeywordCount();
        if (size > MAX_HOT_KEYWORDS * 2L) {
            suggestionCacheStore.removeHotKeywordRange(0, size - MAX_HOT_KEYWORDS - 1);
        }

        if (userId != null) {
            suggestionCacheStore.recordUserHistory(userId, normalizedKeyword, MAX_HISTORY_SIZE, Duration.ofDays(30));
        }

        log.debug("Recorded search: keyword={}, userId={}", keyword, userId);
    }

    public void clearUserHistory(String userId) {
        if (userId != null) {
            suggestionCacheStore.clearUserHistory(userId);
            log.info("Cleared search history for user: {}", userId);
        }
    }
}
