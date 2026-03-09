package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.util.List;

/**
 * 文章更新事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostUpdatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

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
     * 标签
     */
    private final List<String> tags;

    @JsonCreator
    public PostUpdatedEvent(@JsonProperty("eventId") String eventId,
                            @JsonProperty("occurredAt") java.time.LocalDateTime occurredAt,
                            @JsonProperty("postId") Long postId,
                            @JsonProperty("title") String title,
                            @JsonProperty("content") String content,
                            @JsonProperty("excerpt") String excerpt,
                            @JsonProperty("tags") List<String> tags) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.excerpt = excerpt;
        this.tags = tags;
    }

    public PostUpdatedEvent(Long postId, String title, String content, String excerpt, List<String> tags) {
        this(null, null, postId, title, content, excerpt, tags);
    }

    @Override
    public String getTag() {
        return "updated";
    }
}
