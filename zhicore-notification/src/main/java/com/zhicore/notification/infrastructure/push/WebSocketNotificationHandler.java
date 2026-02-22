package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.NotificationPushDTO;
import com.zhicore.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 通知处理器
 * 负责通过 WebSocket 向用户推送通知
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationHandler {

    private final SimpMessagingTemplate messagingTemplate;

    // 通知目的地
    private static final String NOTIFICATION_DESTINATION = "/queue/notifications";
    private static final String UNREAD_COUNT_DESTINATION = "/queue/unread-count";
    private static final String ANNOUNCEMENT_DESTINATION = "/topic/announcements";

    // 在线用户 session 映射
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * 发送通知给指定用户
     *
     * @param userId 用户ID
     * @param notification 通知
     */
    public void sendNotification(String userId, Notification notification) {
        NotificationPushDTO pushDTO = NotificationPushDTO.from(notification);
        messagingTemplate.convertAndSendToUser(
                userId,
                NOTIFICATION_DESTINATION,
                pushDTO
        );
        log.debug("WebSocket推送通知: userId={}, notificationId={}", userId, notification.getId());
    }

    /**
     * 发送未读计数更新给指定用户
     *
     * @param userId 用户ID
     * @param unreadCount 未读数量
     */
    public void sendUnreadCount(String userId, int unreadCount) {
        Map<String, Object> payload = Map.of("unreadCount", unreadCount);
        messagingTemplate.convertAndSendToUser(
                userId,
                UNREAD_COUNT_DESTINATION,
                payload
        );
        log.debug("WebSocket推送未读计数: userId={}, count={}", userId, unreadCount);
    }

    /**
     * 广播系统公告给所有在线用户
     *
     * @param title 公告标题
     * @param content 公告内容
     */
    public void broadcastAnnouncement(String title, String content) {
        Map<String, Object> payload = Map.of(
                "title", title,
                "content", content,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend(ANNOUNCEMENT_DESTINATION, payload);
        log.info("WebSocket广播系统公告: title={}", title);
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(String userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 处理 WebSocket 连接建立事件
     */
    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            String userId = user.getName();
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(event.getMessage().getHeaders().get("simpSessionId", String.class));
            log.info("用户WebSocket连接: userId={}, onlineUsers={}", userId, userSessions.size());
        }
    }

    /**
     * 处理 WebSocket 断开连接事件
     */
    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            String userId = user.getName();
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(event.getSessionId());
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.info("用户WebSocket断开: userId={}, onlineUsers={}", userId, userSessions.size());
        }
    }
}
