package com.zhicore.notification.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 评论流实时提示载荷。
 */
@Value
@Builder
public class CommentStreamHintPayload {

    String eventId;

    String eventType;

    @JsonSerialize(using = ToStringSerializer.class)
    Long postId;

    @JsonSerialize(using = ToStringSerializer.class)
    Long commentId;

    @JsonSerialize(using = ToStringSerializer.class)
    Long parentId;

    Instant occurredAt;
}
