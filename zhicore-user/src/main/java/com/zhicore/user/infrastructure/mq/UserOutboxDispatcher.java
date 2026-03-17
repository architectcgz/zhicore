package com.zhicore.user.infrastructure.mq;

import com.zhicore.common.dispatcher.ClaimBasedDispatcher;
import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.infrastructure.config.UserOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务 outbox 派发器。
 *
 * <p>采用 claim-based 模型：
 * - afterCommit 主动唤醒
 * - 多 worker 并发 claim
 * - 同一 shardingKey 只放行最早未完成事件，避免乱序
 */
@Slf4j
@Component
public class UserOutboxDispatcher extends ClaimBasedDispatcher<OutboxEvent> {

    private final OutboxEventRepository outboxEventRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final UserOutboxProperties properties;

    public UserOutboxDispatcher(OutboxEventRepository outboxEventRepository,
                                RocketMQTemplate rocketMQTemplate,
                                UserOutboxProperties properties,
                                @Qualifier("userOutboxExecutor") TaskExecutor taskExecutor,
                                TransactionOperations transactionOperations) {
        super(
                "user-outbox",
                properties.getWorkerCount(),
                Duration.ofSeconds(properties.getClaimTimeoutSeconds()),
                taskExecutor,
                transactionOperations
        );
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void dispatch() {
        signal();
    }

    @Override
    protected long sweepIntervalMillis() {
        return properties.getScanInterval();
    }

    @Override
    protected int batchSize() {
        return properties.getBatchSize();
    }

    @Override
    protected List<OutboxEvent> claimBatch(String workerId, int limit, Duration claimTimeout) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reclaimBefore = now.minusSeconds(claimTimeout.toSeconds());
        return outboxEventRepository.claimRetryableEvents(now, reclaimBefore, workerId, limit);
    }

    @Override
    protected void handleClaimed(OutboxEvent event) {
        Message<String> message = MessageBuilder
                .withPayload(event.getPayload())
                .setHeader("KEYS", event.getShardingKey())
                .build();

        String destination = event.getTopic() + ":" + event.getTag();
        SendResult result = rocketMQTemplate.syncSend(destination, message);

        event.setStatus(OutboxEventStatus.SUCCEEDED);
        event.setSentAt(LocalDateTime.now());
        event.setErrorMessage(null);
        event.clearClaim();
        outboxEventRepository.update(event);

        log.info("User outbox published: eventId={}, msgId={}, topic={}, tag={}",
                event.getId(), result.getMsgId(), event.getTopic(), event.getTag());
    }

    @Override
    protected void handleFailure(OutboxEvent event, Exception exception) {
        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        retryCount++;

        LocalDateTime now = LocalDateTime.now();
        event.setRetryCount(retryCount);
        event.setErrorMessage(exception.getMessage());
        event.clearClaim();

        if (retryCount >= event.getMaxRetries()) {
            event.setStatus(OutboxEventStatus.DEAD);
            event.setNextAttemptAt(now);
            log.error("User outbox dead after retries: eventId={}, topic={}, tag={}",
                    event.getId(), event.getTopic(), event.getTag(), exception);
        } else {
            event.setStatus(OutboxEventStatus.FAILED);
            event.setNextAttemptAt(now.plusSeconds(backoffSeconds(retryCount)));
            log.warn("User outbox publish failed, will retry: eventId={}, retryCount={}, nextAttemptAt={}",
                    event.getId(), retryCount, event.getNextAttemptAt(), exception);
        }

        outboxEventRepository.update(event);
    }

    public void retryDeadEvents() {
        List<OutboxEvent> deadEvents = outboxEventRepository.findByStatus(OutboxEventStatus.DEAD);
        if (deadEvents.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (OutboxEvent event : deadEvents) {
            event.setStatus(OutboxEventStatus.PENDING);
            event.setRetryCount(0);
            event.setErrorMessage(null);
            event.setSentAt(null);
            event.setNextAttemptAt(now);
            event.clearClaim();
            outboxEventRepository.update(event);
        }

        signal();
        log.info("Reset {} dead user outbox events to PENDING", deadEvents.size());
    }

    private long backoffSeconds(int retryCount) {
        long value = 1L << Math.min(retryCount - 1, 20);
        return Math.min(value, properties.getMaxBackoffSeconds());
    }
}
