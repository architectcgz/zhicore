package com.blog.search.application.service;

import com.blog.search.domain.repository.PostSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SuggestionService 单元测试
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuggestionService 测试")
class SuggestionServiceTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
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
            
            when(zSetOperations.reverseRange(eq("search:hot:keywords"), eq(0L), eq(19L)))
                .thenReturn(hotKeywords);
            when(listOperations.range(eq("search:history:user-001"), eq(0L), eq(9L)))
                .thenReturn(Collections.emptyList());
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

            // When
            List<String> suggestions = suggestionService.getSuggestions(prefix, userId, limit);

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
            
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptySet());
            when(listOperations.range(eq("search:history:user-001"), eq(0L), eq(9L)))
                .thenReturn(Arrays.asList("java", "javascript", "python"));
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

            // When
            List<String> suggestions = suggestionService.getSuggestions(prefix, userId, limit);

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
            
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptySet());
            when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
            when(postSearchRepository.suggest(prefix, limit))
                .thenReturn(Arrays.asList("Docker 入门", "Docker Compose"));

            // When
            List<String> suggestions = suggestionService.getSuggestions(prefix, userId, limit);

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
            
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptySet());
            when(postSearchRepository.suggest(prefix, limit))
                .thenReturn(Arrays.asList("test1", "test2"));

            // When
            List<String> suggestions = suggestionService.getSuggestions(prefix, null, limit);

            // Then
            assertNotNull(suggestions);
            assertEquals(2, suggestions.size());
            // 不应该调用用户历史
            verify(listOperations, never()).range(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("获取建议 - 去重")
        void getSuggestions_Deduplicate() {
            // Given
            String prefix = "spr";
            String userId = "user-001";
            int limit = 5;
            
            Set<String> hotKeywords = new LinkedHashSet<>(Arrays.asList("spring boot"));
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(hotKeywords);
            when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenReturn(Arrays.asList("spring boot", "spring cloud"));
            when(postSearchRepository.suggest(anyString(), anyInt()))
                .thenReturn(Arrays.asList("spring boot", "spring mvc"));

            // When
            List<String> suggestions = suggestionService.getSuggestions(prefix, userId, limit);

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
            
            when(zSetOperations.size(anyString())).thenReturn(10L);

            // When
            suggestionService.recordSearch(keyword, userId);

            // Then
            verify(zSetOperations).incrementScore("search:hot:keywords", "spring boot", 1);
            verify(listOperations).remove("search:history:user-001", 0, "spring boot");
            verify(listOperations).leftPush("search:history:user-001", "spring boot");
            verify(listOperations).trim("search:history:user-001", 0, 9);
        }

        @Test
        @DisplayName("记录搜索 - 无用户ID时只更新热门词")
        void recordSearch_NoUserId() {
            // Given
            String keyword = "Java";
            
            when(zSetOperations.size(anyString())).thenReturn(10L);

            // When
            suggestionService.recordSearch(keyword, null);

            // Then
            verify(zSetOperations).incrementScore("search:hot:keywords", "java", 1);
            verify(listOperations, never()).leftPush(anyString(), anyString());
        }

        @Test
        @DisplayName("记录搜索 - 空关键词不处理")
        void recordSearch_EmptyKeyword() {
            // When
            suggestionService.recordSearch("", "user-001");
            suggestionService.recordSearch(null, "user-001");
            suggestionService.recordSearch("   ", "user-001");

            // Then
            verify(zSetOperations, never()).incrementScore(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("记录搜索 - 热门词数量超限时清理")
        void recordSearch_CleanupHotKeywords() {
            // Given
            String keyword = "test";
            when(zSetOperations.size("search:hot:keywords")).thenReturn(50L);

            // When
            suggestionService.recordSearch(keyword, null);

            // Then
            verify(zSetOperations).removeRange("search:hot:keywords", 0, 29);
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
            when(zSetOperations.reverseRange("search:hot:keywords", 0, limit - 1))
                .thenReturn(mockKeywords);

            // When
            List<String> hotKeywords = suggestionService.getHotKeywords(limit);

            // Then
            assertNotNull(hotKeywords);
            assertEquals(3, hotKeywords.size());
        }

        @Test
        @DisplayName("获取热门搜索词 - 无数据")
        void getHotKeywords_Empty() {
            // Given
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(null);

            // When
            List<String> hotKeywords = suggestionService.getHotKeywords(10);

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
            when(listOperations.range("search:history:user-001", 0, limit - 1))
                .thenReturn(mockHistory);

            // When
            List<String> history = suggestionService.getUserHistory(userId, limit);

            // Then
            assertNotNull(history);
            assertEquals(3, history.size());
        }

        @Test
        @DisplayName("获取用户搜索历史 - 无用户ID")
        void getUserHistory_NoUserId() {
            // When
            List<String> history = suggestionService.getUserHistory(null, 10);

            // Then
            assertNotNull(history);
            assertTrue(history.isEmpty());
            verify(listOperations, never()).range(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("清除用户搜索历史")
        void clearUserHistory_Success() {
            // Given
            String userId = "user-001";
            when(redisTemplate.delete("search:history:user-001")).thenReturn(true);

            // When
            suggestionService.clearUserHistory(userId);

            // Then
            verify(redisTemplate).delete("search:history:user-001");
        }

        @Test
        @DisplayName("清除用户搜索历史 - 无用户ID")
        void clearUserHistory_NoUserId() {
            // When
            suggestionService.clearUserHistory(null);

            // Then
            verify(redisTemplate, never()).delete(anyString());
        }
    }
}
