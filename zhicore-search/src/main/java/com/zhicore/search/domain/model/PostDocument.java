package com.zhicore.search.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章搜索文档
 * 
 * 对应 Elasticsearch 中的文章索引文档结构
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDocument {

    /**
     * 文章ID
     */
    private String id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 作者ID
     */
    private String authorId;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 标签列表（嵌套对象）
     */
    private List<TagInfo> tags;
    
    /**
     * 标签信息（嵌套对象）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagInfo {
        /**
         * 标签ID
         */
        private String id;
        
        /**
         * 标签名称
         */
        private String name;
        
        /**
         * 标签slug
         */
        private String slug;
    }

    /**
     * 分类ID
     */
    private String categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 文章状态
     */
    private String status;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 浏览数
     */
    private Long viewCount;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
