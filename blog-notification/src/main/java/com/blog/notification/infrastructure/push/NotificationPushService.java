package com.blog.notification.infrastructure.push;

import com.blog.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知推送服务
 * 负责将通知实时推送给用户
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final WebSocketNotificationHandler webSocketHandler;

    /**
     * 推送通知给用户
     *
     * @param userId 用户ID
     * @param notification 通知
     */
    public void push(String userId, Notification notification) {
        try {
            // 通过WebSocket推送
            webSocketHandler.sendNotification(userId, notification);
            log.debug("推送通知成功: userId={}, notificationId={}", userId, notification.getId());
        } catch (Exception e) {
            log.warn("推送通知失败: userId={}, notificationId={}, error={}",
                    userId, notification.getId(), e.getMessage());
            // 推送失败不影响主流程，通知已经保存到数据库
        }
    }

    /**
     * 推送未读计数更新
     *
     * @param userId 用户ID
     * @param unreadCount 未读数量
     */
    public void pushUnreadCount(String userId, int unreadCount) {
        try {
            webSocketHandler.sendUnreadCount(userId, unreadCount);
            log.debug("推送未读计数成功: userId={}, count={}", userId, unreadCount);
        } catch (Exception e) {
            log.warn("推送未读计数失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
