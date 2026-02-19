package com.blog.search.domain.repository;

import com.blog.search.domain.model.PostDocument;

import java.util.List;
import java.util.Optional;

/**
 * 文章搜索仓储接口
 *
 * @author Blog Team
 */
public interface PostSearchRepository {

    /**
     * 索引文章
     *
     * @param document 文章文档
     */
    void index(PostDocument document);

    /**
     * 更新文章索引
     *
     * @param document 文章文档
     */
    void update(PostDocument document);

    /**
     * 部分更新文章索引（不包含标签）
     *
     * @param postId 文章ID
     * @param title 标题
     * @param content 内容
     * @param excerpt 摘要
     */
    void partialUpdate(String postId, String title, String content, String excerpt);

    /**
     * 删除文章索引
     *
     * @param postId 文章ID
     */
    void delete(String postId);

    /**
     * 根据ID查询文章
     *
     * @param postId 文章ID
     * @return 文章文档
     */
    Optional<PostDocument> findById(String postId);

    /**
     * 搜索文章
     *
     * @param keyword 关键词
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 搜索结果
     */
    SearchResult<PostDocument> search(String keyword, int page, int size);

    /**
     * 搜索建议
     *
     * @param prefix 前缀
     * @param limit 限制数量
     * @return 建议列表
     */
    List<String> suggest(String prefix, int limit);

    /**
     * 初始化索引
     */
    void initIndex();

    /**
     * 检查索引是否存在
     *
     * @return 是否存在
     */
    boolean indexExists();

    /**
     * 搜索结果封装
     */
    record SearchResult<T>(
        List<SearchHit<T>> hits,
        long total,
        int page,
        int size
    ) {
        public int getTotalPages() {
            return (int) Math.ceil((double) total / size);
        }
    }

    /**
     * 搜索命中结果
     */
    record SearchHit<T>(
        T document,
        float score,
        String highlightTitle,
        String highlightContent
    ) {}
}
