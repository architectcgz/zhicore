package com.zhicore.integration.messaging.comment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 评论删除集成事件。
 */
@Getter
public class CommentDeletedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long commentId;
    private final Long postId;
    private final Long authorId;
    private final boolean topLevel;
    private final String deletedBy;

    @JsonCreator
    public CommentDeletedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                          @JsonProperty("occurredAt") Instant occurredAt,
                                          @JsonProperty("commentId") Long commentId,
                                          @JsonProperty("postId") Long postId,
                                          @JsonProperty("authorId") Long authorId,
                                          @JsonProperty("topLevel") boolean topLevel,
                                          @JsonProperty("deletedBy") String deletedBy,
                                          @JsonProperty("aggregateVersion") Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.commentId = commentId;
        this.postId = postId;
        this.authorId = authorId;
        this.topLevel = topLevel;
        this.deletedBy = deletedBy;
    }

    @Override
    public String getTag() {
        return TopicConstants.TAG_COMMENT_DELETED;
    }

    @Override
    public Long getAggregateId() {
        return commentId;
    }
}
