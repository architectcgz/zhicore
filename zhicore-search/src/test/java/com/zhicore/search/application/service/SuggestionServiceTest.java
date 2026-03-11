package com.zhicore.search.application.service;

import com.zhicore.search.application.port.store.SuggestionCacheStore;
import com.zhicore.search.domain.repository.PostSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SuggestionQueryService / SuggestionCommandService 单元测试
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuggestionService 测试")
class SuggestionServiceTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @Mock
    private SuggestionCacheStore suggestionCacheStore;

    private SuggestionQueryService suggestionQueryService;

    private SuggestionCommandService suggestionCommandService;

    @BeforeEach
    void setUp() {
        suggestionQueryService = new SuggestionQueryService(postSearchRepository, suggestionCacheStore);
        suggestionCommandService = new SuggestionCommandService(suggestionCacheStore);
        lenient().when(suggestionCacheStore.getHotKeywords(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(suggestionCacheStore.getUserHistory(anyString(), anyInt())).thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("获取搜索建议测试")
    class GetSuggestionsTests {

        @Test
        @DisplayName("获取建议 - 从热门搜索词匹配")
        void getSuggestions_FromHotKeywords() {
            // Given
            String prefix = "spr";
            String userId = "user-001";
            int limit = 5;
            
            Set<String> hotKeywords = new LinkedHashSet<>(Arrays.asList(
                "spring boot",
                "spring cloud",
                "java"
            ));

            when(suggestionCacheStore.getHotKeywords(20)).thenReturn(new ArrayList<>(hotKeywords));
            when(suggestionCacheStore.getUserHistory("user-001", 10)).thenReturn(Collections.emptyList());
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

            // When
            List<String> suggestions = suggestionQueryService.getSuggestions(prefix, userId, limit);

            // Then
            assertNotNull(suggestions);
            assertEquals(2, suggestions.size());
            assertTrue(suggestions.contains("spring boot"));
            assertTrue(suggestions.contains("spring cloud"));
            assertFalse(suggestions.contains("java")); // 不匹配前缀
        }

        @Test
        @DisplayName("获取建议 - 从用户历史匹配")
        void getSuggestions_FromUserHistory() {
            // Given
            String prefix = "jav";
            String userId = "user-001";
            int limit = 5;
            
            when(suggestionCacheStore.getHotKeywords(20)).thenReturn(Collections.emptyList());
            when(suggestionCacheStore.getUserHistory("user-001", 10))
                .thenReturn(Arrays.asList("java", "javascript", "python"));
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

            // When
            List<String> suggestions = suggestionQueryService.getSuggestions(prefix, userId, limit);

            // Then
            assertNotNull(suggestions);
            assertEquals(2, suggestions.size());
            assertTrue(suggestions.contains("java"));
            assertTrue(suggestions.contains("javascript"));
        }

        @Test
        @DisplayName("获取建议 - 从 ES 前缀匹配")
        void getSuggestions_FromElasticsearch() {
            // Given
            String prefix = "doc";
            String userId = "user-001";
            int limit = 5;
            
            when(suggestionCacheStore.getHotKeywords(20)).thenReturn(Collections.emptyList());
            when(suggestionCacheStore.getUserHistory("user-001", 10)).thenReturn(Collections.emptyList());
            when(postSearchRepository.suggest(prefix, limit))
                .thenReturn(Arrays.asList("Docker 入门", "Docker Compose"));

            // When
            List<String> suggestions = suggestionQueryService.getSuggestions(prefix, userId, limit);

            // Then
            assertNotNull(suggestions);
            assertEquals(2, suggestions.size());
            assertTrue(suggestions.contains("Docker 入门"));
            assertTrue(suggestions.contains("Docker Compose"));
        }

        @Test
        @DisplayName("获取建议 - 无用户ID时跳过历史")
        void getSuggestions_NoUserId() {
            // Given
            String prefix = "test";
            int limit = 5;
            
            when(suggestionCacheStore.getHotKeywords(20)).thenReturn(Collections.emptyList());
            when(postSearchRepository.suggest(prefix, limit))
                .thenReturn(Arrays.asList("test1", "test2"));

            // When
            List<String> suggestions = suggestionQueryService.getSuggestions(prefix, null, limit);

            // Then
            assertNotNull(suggestions);
            assertEquals(2, suggestions.size());
            // 不应该调用用户历史
            verify(suggestionCacheStore, never()).getUserHistory(anyString(), anyInt());
        }

        @Test
        @DisplayName("获取建议 - 去重")
        void getSuggestions_Deduplicate() {
            // Given
            String prefix = "spr";
            String userId = "user-001";
            int limit = 5;
            
            Set<String> hotKeywords = new LinkedHashSet<>(Arrays.asList("spring boot"));
            when(suggestionCacheStore.getHotKeywords(20)).thenReturn(new ArrayList<>(hotKeywords));
            when(suggestionCacheStore.getUserHistory(userId, 10))
                .thenReturn(Arrays.asList("spring boot", "spring cloud"));
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Arrays.asList("spring boot", "spring mvc"));

            // When
            List<String> suggestions = suggestionQueryService.getSuggestions(prefix, userId, limit);

            // Then
            // 应该去重，spring boot 只出现一次
            long springBootCount = suggestions.stream()
                .filter(s -> s.equals("spring boot"))
                .count();
            assertEquals(1, springBootCount);
        }
    }

    @Nested
    @DisplayName("记录搜索测试")
    class RecordSearchTests {

        @Test
        @DisplayName("记录搜索 - 更新热门词和用户历史")
        void recordSearch_UpdateBoth() {
            // Given
            String keyword = "Spring Boot";
            String userId = "user-001";

            // When
            suggestionCommandService.recordSearch(keyword, userId);

            // Then
            verify(suggestionCacheStore).incrementHotKeywordScore("spring boot");
            verify(suggestionCacheStore).getHotKeywordCount();
            verify(suggestionCacheStore).recordUserHistory(eq("user-001"), eq("spring boot"), eq(10), any());
        }

        @Test
        @DisplayName("记录搜索 - 无用户ID时只更新热门词")
        void recordSearch_NoUserId() {
            // Given
            String keyword = "Java";

            // When
            suggestionCommandService.recordSearch(keyword, null);

            // Then
            verify(suggestionCacheStore).incrementHotKeywordScore("java");
            verify(suggestionCacheStore, never()).recordUserHistory(anyString(), anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("记录搜索 - 空关键词不处理")
        void recordSearch_EmptyKeyword() {
            // When
            suggestionCommandService.recordSearch("", "user-001");
            suggestionCommandService.recordSearch(null, "user-001");
            suggestionCommandService.recordSearch("   ", "user-001");

            // Then
            verifyNoInteractions(suggestionCacheStore);
        }

        @Test
        @DisplayName("记录搜索 - 热门词数量超限时清理")
        void recordSearch_CleanupHotKeywords() {
            // Given
            String keyword = "test";
            when(suggestionCacheStore.getHotKeywordCount()).thenReturn(50L);

            // When
            suggestionCommandService.recordSearch(keyword, null);

            // Then
            verify(suggestionCacheStore).removeHotKeywordRange(0, 29);
        }
    }

    @Nested
    @DisplayName("热门搜索词测试")
    class HotKeywordsTests {

        @Test
        @DisplayName("获取热门搜索词")
        void getHotKeywords_Success() {
            // Given
            int limit = 10;
            Set<String> mockKeywords = new LinkedHashSet<>(Arrays.asList(
                "java", "spring", "docker"
            ));
            when(suggestionCacheStore.getHotKeywords(limit)).thenReturn(new ArrayList<>(mockKeywords));

            // When
            List<String> hotKeywords = suggestionQueryService.getHotKeywords(limit);

            // Then
            assertNotNull(hotKeywords);
            assertEquals(3, hotKeywords.size());
        }

        @Test
        @DisplayName("获取热门搜索词 - 无数据")
        void getHotKeywords_Empty() {
            // Given
            when(suggestionCacheStore.getHotKeywords(10)).thenReturn(Collections.emptyList());

            // When
            List<String> hotKeywords = suggestionQueryService.getHotKeywords(10);

            // Then
            assertNotNull(hotKeywords);
            assertTrue(hotKeywords.isEmpty());
        }
    }

    @Nested
    @DisplayName("用户搜索历史测试")
    class UserHistoryTests {

        @Test
        @DisplayName("获取用户搜索历史")
        void getUserHistory_Success() {
            // Given
            String userId = "user-001";
            int limit = 10;
            List<String> mockHistory = Arrays.asList("java", "spring", "docker");
            when(suggestionCacheStore.getUserHistory(userId, limit)).thenReturn(mockHistory);

            // When
            List<String> history = suggestionQueryService.getUserHistory(userId, limit);

            // Then
            assertNotNull(history);
            assertEquals(3, history.size());
        }

        @Test
        @DisplayName("获取用户搜索历史 - 无用户ID")
        void getUserHistory_NoUserId() {
            // When
            List<String> history = suggestionQueryService.getUserHistory(null, 10);

            // Then
            assertNotNull(history);
            assertTrue(history.isEmpty());
            verify(suggestionCacheStore, never()).getUserHistory(anyString(), anyInt());
        }

        @Test
        @DisplayName("清除用户搜索历史")
        void clearUserHistory_Success() {
            String userId = "user-001";

            // When
            suggestionCommandService.clearUserHistory(userId);

            // Then
            verify(suggestionCacheStore).clearUserHistory("user-001");
        }

        @Test
        @DisplayName("清除用户搜索历史 - 无用户ID")
        void clearUserHistory_NoUserId() {
            // When
            suggestionCommandService.clearUserHistory(null);

            // Then
            verify(suggestionCacheStore, never()).clearUserHistory(anyString());
        }
    }
}
