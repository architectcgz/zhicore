package com.zhicore.notification.infrastructure.mq.payload;

import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论流 fanout 事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentStreamFanoutEvent {

    String eventId;

    String postId;

    CommentStreamHintPayload payload;
}
