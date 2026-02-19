package com.blog.post.domain.event;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章创建事件
 *
 * @author Blog Team
 */
@Getter
public class PostCreatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final String postId;

    /**
     * 标题
     */
    private final String title;

    /**
     * 内容
     */
    private final String content;

    /**
     * 摘要
     */
    private final String excerpt;

    /**
     * 作者ID
     */
    private final String authorId;

    /**
     * 作者名称
     */
    private final String authorName;

    /**
     * 标签ID列表
     */
    private final List<String> tagIds;

    /**
     * 分类ID
     */
    private final String categoryId;

    /**
     * 分类名称
     */
    private final String categoryName;

    /**
     * 文章状态
     */
    private final String status;

    /**
     * 发布时间
     */
    private final LocalDateTime publishedAt;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    public PostCreatedEvent(String postId, String title, String content, String excerpt,
                           String authorId, String authorName, List<String> tagIds,
                           String categoryId, String categoryName, String status,
                           LocalDateTime publishedAt, LocalDateTime createdAt,
                           LocalDateTime eventTime) {
        super();
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.excerpt = excerpt;
        this.authorId = authorId;
        this.authorName = authorName;
        this.tagIds = tagIds;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.status = status;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    @Override
    public String getTag() {
        return "POST_CREATED";
    }
}
