package com.zhicore.message.infrastructure.push;

import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.infrastructure.cache.MessageRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 分布式 WebSocket 推送服务
 * 
 * 解决多节点部署下的消息路由问题：
 * 1. 使用 Redis Pub/Sub 进行跨节点消息广播
 * 2. 每个节点订阅同一个 Topic，收到消息后检查目标用户是否连接到本节点
 * 3. 如果是，则通过本地 WebSocket 推送；否则忽略
 * 
 * 消息流程：
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  用户A(节点1) 发送消息给 用户B(节点2)                                      │
 * │                                                                          │
 * │  1. 节点1 发布消息到 Redis Topic: "message:push:broadcast"               │
 * │  2. 所有节点（包括节点1、节点2）都收到这条消息                              │
 * │  3. 每个节点检查目标用户是否连接到本节点                                    │
 * │  4. 节点2 发现用户B连接在本节点，通过本地 WebSocket 推送                    │
 * │  5. 节点1 发现用户B不在本节点，忽略                                        │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedWebSocketPushService {

    private final RedissonClient redissonClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final LocalConnectionRegistry localConnectionRegistry;

    /**
     * STOMP 消息目的地（WebSocket 内部协议细节，不需要外部化）
     */
    private static final String MESSAGE_DESTINATION = "/queue/messages";

    /**
     * 当前节点ID，用于避免重复处理自己发布的消息（可选优化）
     */
    @Value("${spring.application.instance-id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String instanceId;

    /**
     * 初始化：订阅 Redis Topic
     */
    @PostConstruct
    public void init() {
        String pushTopic = MessageRedisKeys.pushBroadcastTopic();
        RTopic topic = redissonClient.getTopic(pushTopic);
        
        // 订阅消息
        topic.addListener(String.class, (channel, messageJson) -> {
            try {
                BroadcastMessage broadcastMessage = JsonUtils.fromJson(messageJson, BroadcastMessage.class);
                if (broadcastMessage != null) {
                    handleBroadcastMessage(broadcastMessage);
                }
            } catch (Exception e) {
                log.error("Failed to handle broadcast message: {}", messageJson, e);
            }
        });
        
        log.info("Distributed WebSocket push service initialized, instanceId={}", instanceId);
    }

    /**
     * 推送消息给用户（分布式）
     * 通过 Redis Pub/Sub 广播到所有节点
     *
     * @param userId 目标用户ID
     * @param message 推送消息
     */
    public void push(String userId, PushMessage message) {
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setTargetUserId(userId);
        broadcastMessage.setMessage(message);
        broadcastMessage.setSourceInstanceId(instanceId);
        broadcastMessage.setTimestamp(System.currentTimeMillis());

        // 发布到 Redis Topic
        RTopic topic = redissonClient.getTopic(MessageRedisKeys.pushBroadcastTopic());
        topic.publish(JsonUtils.toJson(broadcastMessage));
        
        log.debug("Broadcast message published: userId={}, messageId={}", 
                userId, message.getMessageId());
    }

    /**
     * 处理广播消息
     * 检查目标用户是否连接到本节点，如果是则推送
     */
    private void handleBroadcastMessage(BroadcastMessage broadcastMessage) {
        String targetUserId = broadcastMessage.getTargetUserId();
        PushMessage message = broadcastMessage.getMessage();

        // 检查目标用户是否连接到本节点
        if (localConnectionRegistry.isUserConnectedLocally(targetUserId)) {
            // 用户连接在本节点，通过本地 WebSocket 推送
            try {
                messagingTemplate.convertAndSendToUser(
                        targetUserId,
                        MESSAGE_DESTINATION,
                        message
                );
                log.debug("Message pushed locally: userId={}, messageId={}", 
                        targetUserId, message.getMessageId());
            } catch (Exception e) {
                log.error("Failed to push message locally: userId={}, messageId={}", 
                        targetUserId, message.getMessageId(), e);
            }
        } else {
            // 用户不在本节点，忽略
            log.trace("User not connected to this instance, ignoring: userId={}, instanceId={}", 
                    targetUserId, instanceId);
        }
    }

    /**
     * 广播消息结构
     */
    public static class BroadcastMessage {
        private String targetUserId;
        private PushMessage message;
        private String sourceInstanceId;
        private long timestamp;

        public String getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(String targetUserId) {
            this.targetUserId = targetUserId;
        }

        public PushMessage getMessage() {
            return message;
        }

        public void setMessage(PushMessage message) {
            this.message = message;
        }

        public String getSourceInstanceId() {
            return sourceInstanceId;
        }

        public void setSourceInstanceId(String sourceInstanceId) {
            this.sourceInstanceId = sourceInstanceId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
