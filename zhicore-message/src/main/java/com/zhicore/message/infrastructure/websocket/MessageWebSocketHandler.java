package com.zhicore.message.infrastructure.websocket;

import com.zhicore.message.infrastructure.push.Device;
import com.zhicore.message.infrastructure.push.DeviceRegistry;
import com.zhicore.message.infrastructure.push.DeviceType;
import com.zhicore.message.infrastructure.push.LocalConnectionRegistry;
import com.zhicore.message.infrastructure.push.MultiChannelPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息处理器
 *
 * @author ZhiCore Team
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketHandler {

    private final DeviceRegistry deviceRegistry;
    private final LocalConnectionRegistry localConnectionRegistry;
    private final MultiChannelPushService pushService;

    // 存储 session ID 到 device ID 的映射
    private final Map<String, String> sessionDeviceMap = new ConcurrentHashMap<>();
    // 存储 session ID 到 user ID 的映射（用于断开连接时注销本地连接）
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * 处理 WebSocket 连接建立事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Principal user = headers.getUser();
        String sessionId = headers.getSessionId();

        if (user != null && sessionId != null) {
            String userId = user.getName();
            String deviceId = "web-" + sessionId;

            // 注册设备到全局注册表（Redis）
            Device device = Device.builder()
                    .deviceId(deviceId)
                    .userId(userId)
                    .type(DeviceType.WEB)
                    .connectionId(sessionId)
                    .connectedAt(LocalDateTime.now())
                    .build();
            deviceRegistry.register(device);

            // 注册到本地连接注册表（用于分布式消息路由）
            localConnectionRegistry.register(userId, sessionId);

            // 存储映射
            sessionDeviceMap.put(sessionId, deviceId);
            sessionUserMap.put(sessionId, userId);

            // 推送离线消息
            pushService.pushOfflineMessages(userId, device);

            log.info("WebSocket connected: userId={}, sessionId={}, localUsers={}, localConnections={}", 
                    userId, sessionId, 
                    localConnectionRegistry.getLocalUserCount(),
                    localConnectionRegistry.getLocalConnectionCount());
        }
    }

    /**
     * 处理 WebSocket 断开连接事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();

        if (sessionId != null) {
            String deviceId = sessionDeviceMap.remove(sessionId);
            String userId = sessionUserMap.remove(sessionId);
            
            if (deviceId != null) {
                // 从全局注册表注销
                deviceRegistry.unregister(deviceId);
            }
            
            if (userId != null) {
                // 从本地连接注册表注销
                localConnectionRegistry.unregister(userId, sessionId);
            }
            
            log.info("WebSocket disconnected: sessionId={}, deviceId={}, userId={}", 
                    sessionId, deviceId, userId);
        }
    }

    /**
     * 处理心跳消息
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            log.debug("Heartbeat received: userId={}", user.getName());
        }
    }

    /**
     * 处理消息已读确认
     */
    @MessageMapping("/read")
    public void handleReadAck(@Payload ReadAckPayload payload, SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            log.debug("Read ack received: userId={}, conversationId={}", 
                    user.getName(), payload.getConversationId());
            // 这里可以触发已读状态同步
        }
    }

    /**
     * 已读确认消息载荷
     */
    public static class ReadAckPayload {
        private Long conversationId;
        private Long lastReadMessageId;

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public Long getLastReadMessageId() {
            return lastReadMessageId;
        }

        public void setLastReadMessageId(Long lastReadMessageId) {
            this.lastReadMessageId = lastReadMessageId;
        }
    }
}
