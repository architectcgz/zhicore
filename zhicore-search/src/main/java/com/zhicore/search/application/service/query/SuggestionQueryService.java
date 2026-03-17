package com.zhicore.search.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.search.application.port.store.SuggestionCacheStore;
import com.zhicore.search.application.sentinel.SearchSentinelHandlers;
import com.zhicore.search.application.sentinel.SearchSentinelResources;
import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索建议查询服务。
 */
@Service
@RequiredArgsConstructor
public class SuggestionQueryService {

    private static final int MAX_HOT_KEYWORDS = 20;
    private static final int MAX_HISTORY_SIZE = 10;

    private final PostSearchRepository postSearchRepository;
    private final SuggestionCacheStore suggestionCacheStore;

    @SentinelResource(
            value = SearchSentinelResources.GET_SUGGESTIONS,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleSuggestionsBlocked"
    )
    public List<String> getSuggestions(String prefix, String userId, int limit) {
        List<String> suggestions = new ArrayList<>();

        List<String> hotMatches = getHotKeywordsMatching(prefix, limit);
        suggestions.addAll(hotMatches);

        if (userId != null && suggestions.size() < limit) {
            List<String> historyMatches = getUserHistoryMatching(userId, prefix, limit - suggestions.size());
            for (String match : historyMatches) {
                if (!suggestions.contains(match)) {
                    suggestions.add(match);
                }
            }
        }

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

    @SentinelResource(
            value = SearchSentinelResources.GET_HOT_KEYWORDS,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleHotKeywordsBlocked"
    )
    public List<String> getHotKeywords(int limit) {
        return suggestionCacheStore.getHotKeywords(limit);
    }

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
