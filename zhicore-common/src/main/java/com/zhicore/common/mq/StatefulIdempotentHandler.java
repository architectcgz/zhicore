package com.zhicore.common.mq;

import com.zhicore.common.cache.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 有状态幂等处理器
 * 
 * 使用 Redis 存储消息处理状态，确保消息不被重复消费。
 *
 * 处理中的短 TTL 用于避免消费者崩溃后长时间占锁；
 * 已完成的长 TTL 用于覆盖消息重放、延迟重投等场景。
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatefulIdempotentHandler {

    private static String keyPrefix() {
        return CacheConstants.withNamespace("mq") + ":idempotent:";
    }
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_COMPLETED = "completed";

    private final StringRedisTemplate redisTemplate;

    @Value("${mq.idempotent.processing-expire-minutes:15}")
    private long processingExpireMinutes;

    @Value("${mq.idempotent.completed-expire-hours:168}")
    private long completedExpireHours;

    /**
     * 尝试获取消息处理权
     * 
     * @param messageId 消息ID
     * @return true 表示获取成功，可以处理；false 表示消息正在处理或已处理
     */
    public boolean tryAcquire(String messageId) {
        String key = keyPrefix() + messageId;
        
        // 使用 SETNX 尝试设置状态为 processing
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, STATUS_PROCESSING, processingExpireDuration());
        
        if (Boolean.TRUE.equals(success)) {
            log.debug("Acquired processing lock for message: {}", messageId);
            return true;
        }

        // 检查是否已完成
        String status = redisTemplate.opsForValue().get(key);
        if (STATUS_COMPLETED.equals(status)) {
            log.debug("Message already processed: {}", messageId);
            return false;
        }

        // 正在处理中
        log.debug("Message is being processed by another consumer: {}", messageId);
        return false;
    }

    /**
     * 标记消息处理完成
     *
     * @param messageId 消息ID
     */
    public void markCompleted(String messageId) {
        String key = keyPrefix() + messageId;
        redisTemplate.opsForValue().set(key, STATUS_COMPLETED, completedExpireDuration());
        log.debug("Marked message as completed: {}", messageId);
    }

    /**
     * 释放处理锁（处理失败时调用）
     *
     * @param messageId 消息ID
     */
    public void release(String messageId) {
        String key = keyPrefix() + messageId;
        redisTemplate.delete(key);
        log.debug("Released processing lock for message: {}", messageId);
    }

    /**
     * 检查消息是否已处理
     *
     * @param messageId 消息ID
     * @return true 表示已处理
     */
    public boolean isProcessed(String messageId) {
        String key = keyPrefix() + messageId;
        String status = redisTemplate.opsForValue().get(key);
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * 带幂等性的消息处理
     *
     * @param messageId 消息ID
     * @param handler 消息处理逻辑
     * @return true 表示处理成功或已处理，false 表示处理失败
     */
    public boolean handleIdempotent(String messageId, Runnable handler) {
        if (!tryAcquire(messageId)) {
            return true; // 已处理或正在处理
        }

        try {
            handler.run();
            markCompleted(messageId);
            return true;
        } catch (Exception e) {
            log.error("Failed to process message: {}", messageId, e);
            release(messageId);
            throw e;
        }
    }

    private Duration processingExpireDuration() {
        return Duration.ofMinutes(Math.max(1, processingExpireMinutes));
    }

    private Duration completedExpireDuration() {
        return Duration.ofHours(Math.max(1, completedExpireHours));
    }
}
