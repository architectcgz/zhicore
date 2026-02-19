package com.blog.user.infrastructure.mq;

import com.blog.user.domain.model.OutboxEvent;
import com.blog.user.domain.model.OutboxEventStatus;
import com.blog.user.domain.repository.OutboxEventRepository;
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
 * <h3>重试策略</h3>
 * <ul>
 *   <li>默认最大重试次数：3 次</li>
 *   <li>每次失败后递增 retry_count</li>
 *   <li>超过最大重试次数后标记为 FAILED</li>
 *   <li>FAILED 事件需要人工介入或手动重试</li>
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
     * 定时扫描 outbox_events 表并发送到 RocketMQ
     * 
     * <p>执行频率：每 5 秒一次</p>
     * <p>批量大小：每次最多处理 100 条</p>
     * 
     * <p><strong>注意：</strong>每条事件使用独立事务，避免批量回滚</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
            .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100);
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.info("Publishing {} pending outbox events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            // 【关键】每条事件使用独立事务
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
            
            // 增加重试次数
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(e.getMessage());
            
            // 如果超过最大重试次数，标记为失败
            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(OutboxEventStatus.FAILED);
                log.error("Outbox event failed after {} retries: eventId={}, topic={}, tag={}", 
                    event.getMaxRetries(), event.getId(), event.getTopic(), event.getTag());
                
                // TODO: 发送告警（通过监控系统）
                // alertService.sendAlert("Outbox Event Failed", 
                //     String.format("Event %s failed after %d retries", event.getId(), event.getMaxRetries()));
            }
            
            outboxEventRepository.update(event);
        }
    }
    
    /**
     * 重试失败的事件（手动触发或定时任务）
     * 
     * <p>将 FAILED 状态的事件重置为 PENDING，重新尝试发送</p>
     * 
     * <p><strong>使用场景：</strong></p>
     * <ul>
     *   <li>RocketMQ 恢复后手动重试</li>
     *   <li>修复问题后批量重试</li>
     *   <li>定期清理失败事件</li>
     * </ul>
     */
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository
            .findByStatus(OutboxEventStatus.FAILED);
        
        if (failedEvents.isEmpty()) {
            log.info("No failed events to retry");
            return;
        }
        
        for (OutboxEvent event : failedEvents) {
            // 重置状态为 PENDING，重新尝试发送
            event.setStatus(OutboxEventStatus.PENDING);
            event.setRetryCount(0);
            event.setErrorMessage(null);
            outboxEventRepository.update(event);
        }
        
        log.info("Reset {} failed events to PENDING for retry", failedEvents.size());
    }
}
