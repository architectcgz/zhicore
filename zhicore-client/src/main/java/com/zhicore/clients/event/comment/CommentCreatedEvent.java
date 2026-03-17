package com.zhicore.api.event.comment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 评论创建事件
 */
@Getter
public class CommentCreatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    public static final String TAG = "comment-created";

    private final Long commentId;
    private final Long postId;
    private final Long postOwnerId;
    private final Long commentAuthorId;
    private final Long parentId;
    private final Long replyToUserId;
    private final String commentContent;

    @JsonCreator
    public CommentCreatedEvent(@JsonProperty("eventId") String eventId,
                               @JsonProperty("occurredAt") java.time.Instant occurredAt,
                               @JsonProperty("commentId") Long commentId,
                               @JsonProperty("postId") Long postId,
                               @JsonProperty("postOwnerId") Long postOwnerId,
                               @JsonProperty("commentAuthorId") Long commentAuthorId,
                               @JsonProperty("parentId") Long parentId,
                               @JsonProperty("replyToUserId") Long replyToUserId,
                               @JsonProperty("commentContent") String commentContent) {
        super(eventId, occurredAt);
        this.commentId = commentId;
        this.postId = postId;
        this.postOwnerId = postOwnerId;
        this.commentAuthorId = commentAuthorId;
        this.parentId = parentId;
        this.replyToUserId = replyToUserId;
        this.commentContent = commentContent;
    }

    public CommentCreatedEvent(Long commentId, Long postId, Long postOwnerId,
                               Long commentAuthorId, Long parentId, Long replyToUserId,
                               String commentContent) {
        this(null, null, commentId, postId, postOwnerId, commentAuthorId, parentId, replyToUserId, commentContent);
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
