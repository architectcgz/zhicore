package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
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

    @JsonCreator
    public PostPublishedEvent(@JsonProperty("eventId") String eventId,
                              @JsonProperty("occurredAt") java.time.Instant occurredAt,
                              @JsonProperty("postId") Long postId,
                              @JsonProperty("authorId") Long authorId,
                              @JsonProperty("title") String title,
                              @JsonProperty("excerpt") String excerpt,
                              @JsonProperty("tags") List<String> tags) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.authorId = authorId;
        this.title = title;
        this.excerpt = excerpt;
        this.tags = tags;
    }

    public PostPublishedEvent(Long postId, Long authorId, String title, String excerpt, List<String> tags) {
        this(null, null, postId, authorId, title, excerpt, tags);
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
