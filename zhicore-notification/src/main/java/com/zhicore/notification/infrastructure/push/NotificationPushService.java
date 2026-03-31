package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final WebSocketNotificationHandler webSocketHandler;
    private final NotificationRepository notificationRepository;
    private final NotificationAggregationService notificationAggregationService;

    public boolean push(String userId, Notification notification) {
        try {
            AggregatedNotificationVO aggregatedNotification =
                    notificationAggregationService.getAggregatedNotificationForPush(notification);
            webSocketHandler.sendNotification(userId, aggregatedNotification);
            log.debug("推送通知成功: userId={}, notificationId={}", userId, notification.getId());
        } catch (Exception e) {
            log.warn("推送通知失败: userId={}, notificationId={}, error={}",
                    userId, notification.getId(), e.getMessage());
            return false;
        }

        pushUnreadCount(userId, notificationRepository.countUnread(Long.valueOf(userId)));
        return true;
    }

    public void pushUnreadCount(String userId, int unreadCount) {
        try {
            webSocketHandler.sendUnreadCount(userId, unreadCount);
            log.debug("推送未读计数成功: userId={}, count={}", userId, unreadCount);
        } catch (Exception e) {
            log.warn("推送未读计数失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    public void broadcastPostCommentStreamHint(Long postId, Map<String, Object> payload) {
        try {
            webSocketHandler.broadcastPostCommentStream(postId, payload);
        } catch (Exception e) {
            log.warn("广播评论流提示失败: postId={}, error={}", postId, e.getMessage());
        }
    }
}
