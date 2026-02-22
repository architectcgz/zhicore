package com.zhicore.message.infrastructure.push;

import com.zhicore.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 离线消息存储
 * 使用 Redis List 存储离线消息
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineMessageStore {

    private final RedissonClient redissonClient;

    private static final String OFFLINE_MESSAGE_KEY_PREFIX = "message:offline:";

    @Value("${message.offline-message-expire-days:7}")
    private int offlineMessageExpireDays;

    /**
     * 保存离线消息
     *
     * @param userId 用户ID
     * @param message 推送消息
     */
    public void save(String userId, PushMessage message) {
        String key = OFFLINE_MESSAGE_KEY_PREFIX + userId;
        RList<String> offlineMessages = redissonClient.getList(key);
        
        // 序列化消息
        String messageJson = JsonUtils.toJson(message);
        offlineMessages.add(messageJson);
        
        // 设置过期时间
        offlineMessages.expire(Duration.ofDays(offlineMessageExpireDays));
        
        log.debug("Offline message saved: userId={}, messageId={}", userId, message.getMessageId());
    }

    /**
     * 获取并清除离线消息
     *
     * @param userId 用户ID
     * @return 离线消息列表
     */
    public List<PushMessage> getAndClear(String userId) {
        String key = OFFLINE_MESSAGE_KEY_PREFIX + userId;
        RList<String> offlineMessages = redissonClient.getList(key);
        
        if (!offlineMessages.isExists() || offlineMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有消息
        List<String> messageJsonList = offlineMessages.readAll();
        
        // 清除消息
        offlineMessages.delete();
        
        // 反序列化
        List<PushMessage> messages = messageJsonList.stream()
                .map(json -> JsonUtils.fromJson(json, PushMessage.class))
                .filter(m -> m != null)
                .collect(Collectors.toList());
        
        log.info("Offline messages retrieved and cleared: userId={}, count={}", userId, messages.size());
        
        return messages;
    }

    /**
     * 获取离线消息数量
     *
     * @param userId 用户ID
     * @return 消息数量
     */
    public int getCount(String userId) {
        String key = OFFLINE_MESSAGE_KEY_PREFIX + userId;
        RList<String> offlineMessages = redissonClient.getList(key);
        return offlineMessages.isExists() ? offlineMessages.size() : 0;
    }
}
