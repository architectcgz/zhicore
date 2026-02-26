package com.zhicore.user.infrastructure.mq;

import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件发布器
 * 
 * <p>定时扫描 outbox_events 表中的 PENDING 事件并发送到 RocketMQ</p>
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>每 5 秒扫描一次 outbox_events 表</li>
 *   <li>查询 status=PENDING 的事件（最多 100 条）</li>
 *   <li>每条事件使用独立事务发送到 RocketMQ</li>
 *   <li>发送成功后更新 status=SENT</li>
 *   <li>发送失败后递增 retry_count，超过最大重试次数标记为 FAILED</li>
 * </ol>
 * 
 * <h3>事务隔离</h3>
 * <p>每条事件使用 REQUIRES_NEW 事务，确保：</p>
 * <ul>
 *   <li>单条事件失败不影响其他事件</li>
 *   <li>状态更新立即提交，避免重复发送</li>
 *   <li>批量处理时不会因为一条失败导致全部回滚</li>
 * </ul>
 * 
 * <h3>重试策略（指数退避）</h3>
 * <ul>
 *   <li>默认最大重试次数：10 次</li>
 *   <li>退避间隔：1s → 2s → 4s → ... → 最大 5min</li>
 *   <li>失败后标记为 FAILED，等待 next_retry_at 到达后重试</li>
 *   <li>超过最大重试次数后标记为 DEAD，需人工介入</li>
 * </ul>
 * 
 * @author System
 * @since 2026-02-19
 * @see OutboxEvent
 * @see OutboxEventRepository
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RocketMQTemplate rocketMQTemplate;
    
    /**
     * 定时扫描待投递事件（PENDING/FAILED 且 next_retry_at <= NOW）
     *
     * <p>执行频率：每 5 秒一次，每次最多处理 100 条</p>
     * <p>使用 FOR UPDATE SKIP LOCKED 支持多实例并行投递</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
            .findRetryableEvents(100);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Publishing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }
    
    /**
     * 发送单个事件（独立事务）
     * 
     * <p>使用 REQUIRES_NEW 确保每条事件的状态更新独立提交，
     * 避免批量回滚导致重复发送</p>
     * 
     * @param event Outbox 事件
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishSingleEvent(OutboxEvent event) {
        try {
            // 构建 RocketMQ 消息
            Message<String> message = MessageBuilder
                .withPayload(event.getPayload())
                .setHeader("KEYS", event.getShardingKey())  // 设置消息 Key
                .build();
            
            // 发送到 RocketMQ
            String destination = event.getTopic() + ":" + event.getTag();
            SendResult result = rocketMQTemplate.syncSend(destination, message);
            
            // 标记为已发送
            event.setStatus(OutboxEventStatus.SENT);
            event.setSentAt(LocalDateTime.now());
            outboxEventRepository.update(event);
            
            log.info("Outbox event published: eventId={}, msgId={}, topic={}, tag={}", 
                event.getId(), result.getMsgId(), event.getTopic(), event.getTag());
            
        } catch (Exception e) {
            log.error("Failed to publish outbox event: eventId={}, topic={}, tag={}",
                event.getId(), event.getTopic(), event.getTag(), e);

            event.setErrorMessage(e.getMessage());
            event.scheduleNextRetry();

            if (event.isExhausted()) {
                event.setStatus(OutboxEventStatus.DEAD);
                log.error("Outbox event dead after {} retries: eventId={}, topic={}, tag={}",
                    event.getMaxRetries(), event.getId(), event.getTopic(), event.getTag());
            } else {
                event.setStatus(OutboxEventStatus.FAILED);
                log.warn("Outbox event will retry (attempt {}/{}): eventId={}, nextRetryAt={}",
                    event.getRetryCount(), event.getMaxRetries(), event.getId(), event.getNextRetryAt());
            }

            outboxEventRepository.update(event);
        }
    }
    
    /**
     * 重试死信事件（手动触发）
     *
     * <p>将 DEAD 状态的事件重置为 PENDING，重新尝试发送</p>
     *
     * <p>使用场景：RocketMQ 恢复后手动重投、修复问题后批量重试</p>
     */
    public void retryDeadEvents() {
        List<OutboxEvent> deadEvents = outboxEventRepository
            .findByStatus(OutboxEventStatus.DEAD);

        if (deadEvents.isEmpty()) {
            log.info("No dead events to retry");
            return;
        }

        for (OutboxEvent event : deadEvents) {
            event.setStatus(OutboxEventStatus.PENDING);
            event.setRetryCount(0);
            event.setErrorMessage(null);
            event.setNextRetryAt(LocalDateTime.now());
            outboxEventRepository.update(event);
        }

        log.info("Reset {} dead events to PENDING for retry", deadEvents.size());
    }
}
