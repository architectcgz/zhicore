package com.zhicore.message.infrastructure.cache;

/**
 * 消息服务 Redis Key 常量
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：message:123:device, message:123:offline
 *
 * @author ZhiCore Team
 */
public final class MessageRedisKeys {
    
    private static final String PREFIX = "message";

    private MessageRedisKeys() {
        // 工具类，禁止实例化
    }
    
    /**
     * 分布式 WebSocket 消息推送广播 Topic
     * 用于 Redis Pub/Sub 跨节点广播
     * Key: message:push:broadcast
     */
    public static String pushBroadcastTopic() {
        return PREFIX + ":push:broadcast";
    }
    
    /**
     * 用户设备注册表
     * Key: message:{userId}:device
     */
    public static String deviceRegistry(Long userId) {
        return PREFIX + ":" + userId + ":device";
    }
    
    /**
     * 离线消息存储
     * Key: message:{userId}:offline
     */
    public static String offlineMessages(Long userId) {
        return PREFIX + ":" + userId + ":offline";
    }

    /**
     * 本地消息与 IM 消息的桥接映射。
     * Key: message:im-bridge:{localMessageId}
     */
    public static String imBridgeMapping(Long localMessageId) {
        return PREFIX + ":im-bridge:" + localMessageId;
    }
}
