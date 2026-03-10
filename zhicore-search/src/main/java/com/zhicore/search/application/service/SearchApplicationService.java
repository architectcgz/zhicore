package com.zhicore.search.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.search.application.dto.PostSearchVO;
import com.zhicore.search.application.dto.SearchResultVO;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchHit;
import com.zhicore.search.domain.repository.PostSearchRepository.SearchResult;
import com.zhicore.search.application.sentinel.SearchSentinelHandlers;
import com.zhicore.search.application.sentinel.SearchSentinelResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

    private final PostSearchRepository postSearchRepository;

    /**
     * 搜索文章
     *
     * @param keyword 关键词
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 搜索结果
     */
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

    /**
     * 获取搜索建议
     *
     * @param prefix 前缀
     * @param limit 限制数量
     * @return 建议列表
     */
    public List<String> suggest(String prefix, int limit) {
        log.debug("Getting suggestions: prefix={}, limit={}", prefix, limit);
        return postSearchRepository.suggest(prefix, limit);
    }

    /**
     * 重建文章索引
     *
     * @param document 文章文档
     */
    public void indexPost(PostDocument document) {
        log.info("Indexing post: id={}", document.getId());
        postSearchRepository.index(document);
    }

    /**
     * 更新文章索引（不包含标签）
     *
     * @param postId 文章ID
     * @param title 标题
     * @param content 内容
     * @param excerpt 摘要
     */
    public void updatePostIndex(String postId, String title, String content, String excerpt) {
        log.info("Updating post index: id={}", postId);
        postSearchRepository.partialUpdate(postId, title, content, excerpt);
    }

    /**
     * 删除文章索引
     *
     * @param postId 文章ID
     */
    public void deletePostIndex(String postId) {
        log.info("Deleting post index: id={}", postId);
        postSearchRepository.delete(postId);
    }

    private PostSearchVO convertToVO(SearchHit<PostDocument> hit) {
        PostDocument doc = hit.document();
        
        // 提取标签名称列表
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
