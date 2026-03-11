package com.zhicore.search.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.search.application.dto.PostSearchVO;
import com.zhicore.search.application.dto.SearchResultVO;
import com.zhicore.search.application.sentinel.SearchSentinelHandlers;
import com.zhicore.search.application.sentinel.SearchSentinelResources;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchHit;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索读服务。
 *
 * 负责全文检索与轻量建议查询，不承载索引写入职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryService {

    private final PostSearchRepository postSearchRepository;

    @SentinelResource(
            value = SearchSentinelResources.SEARCH_POSTS,
            blockHandlerClass = SearchSentinelHandlers.class,
            blockHandler = "handleSearchPostsBlocked"
    )
    public SearchResultVO<PostSearchVO> searchPosts(String keyword, int page, int size) {
        log.debug("Searching posts: keyword={}, page={}, size={}", keyword, page, size);

        SearchResult<PostDocument> result = postSearchRepository.search(keyword, page, size);
        List<PostSearchVO> items = result.hits().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return SearchResultVO.of(items, result.total(), page, size);
    }

    public List<String> suggest(String prefix, int limit) {
        log.debug("Getting suggestions: prefix={}, limit={}", prefix, limit);
        return postSearchRepository.suggest(prefix, limit);
    }

    private PostSearchVO convertToVO(SearchHit<PostDocument> hit) {
        PostDocument doc = hit.document();

        List<String> tagNames = null;
        if (doc.getTags() != null) {
            tagNames = doc.getTags().stream()
                    .map(PostDocument.TagInfo::getName)
                    .collect(Collectors.toList());
        }

        return PostSearchVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .highlightTitle(hit.highlightTitle() != null ? hit.highlightTitle() : doc.getTitle())
                .excerpt(doc.getExcerpt())
                .highlightContent(hit.highlightContent())
                .authorId(doc.getAuthorId())
                .authorName(doc.getAuthorName())
                .tags(tagNames)
                .categoryName(doc.getCategoryName())
                .likeCount(doc.getLikeCount())
                .commentCount(doc.getCommentCount())
                .viewCount(doc.getViewCount())
                .publishedAt(doc.getPublishedAt())
                .score(hit.score())
                .build();
    }
}
