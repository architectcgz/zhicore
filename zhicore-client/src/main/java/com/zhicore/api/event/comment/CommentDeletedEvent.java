package com.zhicore.api.event.comment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 评论删除事件
 * 下游消费者：内容服务（递减文章评论计数，仅 isTopLevel=true）
 */
@Getter
public class CommentDeletedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    public static final String TAG = "deleted";

    /** 事件结构版本号，用于下游兼容性处理 */
    private final int version = 1;

    private final Long commentId;
    private final Long postId;
    private final Long authorId;
    private final boolean topLevel;
    /** 删除操作来源：AUTHOR（作者本人）或 ADMIN（管理员） */
    private final String deletedBy;

    @JsonCreator
    public CommentDeletedEvent(@JsonProperty("eventId") String eventId,
                               @JsonProperty("occurredAt") java.time.Instant occurredAt,
                               @JsonProperty("commentId") Long commentId,
                               @JsonProperty("postId") Long postId,
                               @JsonProperty("authorId") Long authorId,
                               @JsonProperty("topLevel") boolean topLevel,
                               @JsonProperty("deletedBy") String deletedBy) {
        super(eventId, occurredAt);
        this.commentId = commentId;
        this.postId = postId;
        this.authorId = authorId;
        this.topLevel = topLevel;
        this.deletedBy = deletedBy;
    }

    public CommentDeletedEvent(Long commentId, Long postId, Long authorId,
                               boolean topLevel, String deletedBy) {
        this(null, null, commentId, postId, authorId, topLevel, deletedBy);
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
