package com.blog.message.infrastructure.push;

import com.blog.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 推送服务
 * 用于 Web 端实时消息推送
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String MESSAGE_DESTINATION = "/queue/messages";

    /**
     * 推送消息到指定用户
     *
     * @param userId 用户ID
     * @param message 推送消息
     */
    public void push(String userId, PushMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    MESSAGE_DESTINATION,
                    message
            );
            log.debug("WebSocket message pushed: userId={}, messageId={}", userId, message.getMessageId());
        } catch (Exception e) {
            log.error("Failed to push WebSocket message: userId={}, messageId={}", 
                    userId, message.getMessageId(), e);
            throw new PushException("WebSocket推送失败", e);
        }
    }

    /**
     * 推送消息到指定连接
     *
     * @param connectionId 连接ID
     * @param message 推送消息
     */
    public void pushToConnection(String connectionId, PushMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    connectionId,
                    MESSAGE_DESTINATION,
                    message
            );
            log.debug("WebSocket message pushed to connection: connectionId={}, messageId={}", 
                    connectionId, message.getMessageId());
        } catch (Exception e) {
            log.error("Failed to push WebSocket message to connection: connectionId={}, messageId={}", 
                    connectionId, message.getMessageId(), e);
            throw new PushException("WebSocket推送失败", e);
        }
    }
}
