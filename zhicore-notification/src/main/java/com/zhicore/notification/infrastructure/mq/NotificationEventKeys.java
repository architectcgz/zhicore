package com.zhicore.notification.infrastructure.mq;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 基于事件生成稳定通知 ID，借助数据库主键实现消费幂等。
 */
public final class NotificationEventKeys {

    private NotificationEventKeys() {
    }

    public static Long notificationId(String eventId, String slot) {
        UUID uuid = UUID.nameUUIDFromBytes((eventId + "#" + slot).getBytes(StandardCharsets.UTF_8));
        long id = uuid.getMostSignificantBits() & Long.MAX_VALUE;
        return id == 0 ? 1L : id;
    }
}
