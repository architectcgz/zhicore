package com.zhicore.notification.infrastructure.mq.payload;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户通知 fanout 事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationFanoutEvent {

    String eventId;

    String userId;

    AggregatedNotificationVO payload;
}
