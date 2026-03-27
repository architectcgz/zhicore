package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知推送服务
 * 负责将通知实时推送给用户
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final WebSocketNotificationHandler webSocketHandler;
    private final NotificationRepository notificationRepository;
    private final NotificationAggregationService notificationAggregationService;

    /**
     * 推送通知给用户
     *
     * @param userId 用户ID
     * @param notification 通知
     */
    public void push(String userId, Notification notification) {
        try {
            AggregatedNotificationVO aggregatedNotification =
                    notificationAggregationService.getAggregatedNotificationForPush(notification);
            // 通过WebSocket推送
            webSocketHandler.sendNotification(userId, aggregatedNotification);
            log.debug("推送通知成功: userId={}, notificationId={}", userId, notification.getId());
        } catch (Exception e) {
            log.warn("推送通知失败: userId={}, notificationId={}, error={}",
                    userId, notification.getId(), e.getMessage());
            // 推送失败不影响主流程，通知已经保存到数据库
            return;
        }

        pushUnreadCount(userId, notificationRepository.countUnread(Long.valueOf(userId)));
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

    /**
     * 广播文章评论流提示。
     *
     * @param postId 文章ID
     * @param payload 实时提示载荷
     */
    public void broadcastCommentStreamHint(String postId, CommentStreamHintPayload payload) {
        try {
            webSocketHandler.sendCommentStreamHint(postId, payload);
        } catch (Exception e) {
            log.warn("广播评论流提示失败: postId={}, commentId={}, error={}",
                    postId, payload.getCommentId(), e.getMessage());
        }
    }
}
