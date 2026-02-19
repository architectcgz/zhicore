package com.blog.api.event.post;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

import java.util.List;

/**
 * 文章发布事件
 */
@Getter
public class PostPublishedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    public static final String TAG = "post-published";

    private final Long postId;
    private final Long authorId;
    private final String title;
    private final String excerpt;
    private final List<String> tags;

    public PostPublishedEvent(Long postId, Long authorId, String title, String excerpt, List<String> tags) {
        super();
        this.postId = postId;
        this.authorId = authorId;
        this.title = title;
        this.excerpt = excerpt;
        this.tags = tags;
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
