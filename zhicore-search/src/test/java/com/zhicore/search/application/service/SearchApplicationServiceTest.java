package com.zhicore.search.application.service;

import com.zhicore.search.application.dto.PostSearchVO;
import com.zhicore.search.application.dto.SearchResultVO;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchHit;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Search query/index service 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Search query/index service 测试")
class SearchApplicationServiceTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @InjectMocks
    private SearchQueryService searchQueryService;

    @InjectMocks
    private SearchIndexCommandService searchIndexCommandService;

    private PostDocument sampleDocument;
    private SearchHit<PostDocument> sampleHit;

    @BeforeEach
    void setUp() {
        sampleDocument = PostDocument.builder()
                .id("1")
                .title("Spring Boot 入门教程")
                .content("这是一篇关于 Spring Boot 的入门教程...")
                .excerpt("Spring Boot 入门")
                .authorId("user-001")
                .authorName("测试作者")
                .tags(Arrays.asList(
                        PostDocument.TagInfo.builder().id("1").name("Spring").slug("spring").build(),
                        PostDocument.TagInfo.builder().id("2").name("Java").slug("java").build()
                ))
                .categoryName("技术")
                .status("PUBLISHED")
                .likeCount(100)
                .commentCount(50)
                .viewCount(1000L)
                .publishedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        sampleHit = new SearchHit<>(
                sampleDocument,
                10.5f,
                "<em>Spring Boot</em> 入门教程",
                "这是一篇关于 <em>Spring Boot</em> 的入门教程..."
        );
    }

    @Nested
    @DisplayName("搜索文章测试")
    class SearchPostsTests {

        @Test
        @DisplayName("搜索文章 - 有结果")
        void searchPostsWithResults() {
            String keyword = "Spring Boot";
            int page = 0;
            int size = 10;

            SearchResult<PostDocument> mockResult = new SearchResult<>(List.of(sampleHit), 1L, page, size);
            when(postSearchRepository.search(keyword, page, size)).thenReturn(mockResult);

            SearchResultVO<PostSearchVO> result = searchQueryService.searchPosts(keyword, page, size);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(page, result.getPage());
            assertEquals(size, result.getSize());
            assertEquals(1, result.getItems().size());

            PostSearchVO vo = result.getItems().get(0);
            assertEquals(sampleDocument.getId(), vo.getId());
            assertEquals(sampleDocument.getTitle(), vo.getTitle());
            assertEquals("<em>Spring Boot</em> 入门教程", vo.getHighlightTitle());
            assertEquals("这是一篇关于 <em>Spring Boot</em> 的入门教程...", vo.getHighlightContent());
            assertEquals(sampleDocument.getAuthorId(), vo.getAuthorId());
            assertEquals(sampleDocument.getAuthorName(), vo.getAuthorName());
            assertEquals(Arrays.asList("Spring", "Java"), vo.getTags());
            assertEquals(10.5f, vo.getScore());

            verify(postSearchRepository).search(keyword, page, size);
        }

        @Test
        @DisplayName("搜索文章 - 无结果")
        void searchPostsNoResults() {
            SearchResult<PostDocument> mockResult = new SearchResult<>(Collections.emptyList(), 0L, 0, 10);
            when(postSearchRepository.search("不存在的关键词", 0, 10)).thenReturn(mockResult);

            SearchResultVO<PostSearchVO> result = searchQueryService.searchPosts("不存在的关键词", 0, 10);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getItems().isEmpty());
            assertFalse(result.isHasNext());
            assertFalse(result.isHasPrevious());
        }

        @Test
        @DisplayName("搜索文章 - 分页")
        void searchPostsPagination() {
            SearchResult<PostDocument> mockResult = new SearchResult<>(List.of(sampleHit), 25L, 1, 10);
            when(postSearchRepository.search("Java", 1, 10)).thenReturn(mockResult);

            SearchResultVO<PostSearchVO> result = searchQueryService.searchPosts("Java", 1, 10);

            assertEquals(25, result.getTotal());
            assertEquals(3, result.getTotalPages());
            assertTrue(result.isHasNext());
            assertTrue(result.isHasPrevious());
        }

        @Test
        @DisplayName("搜索文章 - 无高亮时使用原标题")
        void searchPostsNoHighlight() {
            SearchHit<PostDocument> hitWithoutHighlight = new SearchHit<>(sampleDocument, 5.0f, null, null);
            SearchResult<PostDocument> mockResult = new SearchResult<>(List.of(hitWithoutHighlight), 1L, 0, 10);
            when(postSearchRepository.search(anyString(), anyInt(), anyInt())).thenReturn(mockResult);

            SearchResultVO<PostSearchVO> result = searchQueryService.searchPosts("test", 0, 10);

            PostSearchVO vo = result.getItems().get(0);
            assertEquals(sampleDocument.getTitle(), vo.getHighlightTitle());
            assertNull(vo.getHighlightContent());
        }
    }

    @Nested
    @DisplayName("搜索建议测试")
    class SuggestTests {

        @Test
        @DisplayName("获取搜索建议 - 有结果")
        void suggestWithResults() {
            String prefix = "Spr";
            int limit = 5;
            List<String> mockSuggestions = Arrays.asList("Spring Boot", "Spring Cloud", "Spring MVC");
            when(postSearchRepository.suggest(prefix, limit)).thenReturn(mockSuggestions);

            List<String> suggestions = searchQueryService.suggest(prefix, limit);

            assertNotNull(suggestions);
            assertEquals(3, suggestions.size());
            assertTrue(suggestions.contains("Spring Boot"));
            assertTrue(suggestions.contains("Spring Cloud"));
            assertTrue(suggestions.contains("Spring MVC"));
            verify(postSearchRepository).suggest(prefix, limit);
        }

        @Test
        @DisplayName("获取搜索建议 - 无结果")
        void suggestNoResults() {
            when(postSearchRepository.suggest("xyz", 5)).thenReturn(Collections.emptyList());

            List<String> suggestions = searchQueryService.suggest("xyz", 5);

            assertNotNull(suggestions);
            assertTrue(suggestions.isEmpty());
        }
    }

    @Nested
    @DisplayName("索引操作测试")
    class IndexOperationTests {

        @Test
        @DisplayName("索引文章")
        void indexPostSuccess() {
            searchIndexCommandService.indexPost(sampleDocument);
            verify(postSearchRepository).index(sampleDocument);
        }

        @Test
        @DisplayName("更新文章索引")
        void updatePostIndexSuccess() {
            searchIndexCommandService.updatePostIndex("1", "title", "content", "excerpt");
            verify(postSearchRepository).partialUpdate("1", "title", "content", "excerpt");
        }

        @Test
        @DisplayName("删除文章索引")
        void deletePostIndexSuccess() {
            searchIndexCommandService.deletePostIndex("1");
            verify(postSearchRepository).delete("1");
        }
    }
}
