package com.zhicore.notification.infrastructure.mq.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读数 fanout 事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountFanoutEvent {

    String eventId;

    String userId;

    int unreadCount;
}
