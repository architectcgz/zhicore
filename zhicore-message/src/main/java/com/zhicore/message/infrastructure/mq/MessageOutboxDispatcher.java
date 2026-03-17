package com.zhicore.message.infrastructure.mq;

import com.zhicore.common.dispatcher.ClaimBasedDispatcher;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.service.ImMessageBridgeService;
import com.zhicore.message.infrastructure.config.MessageOutboxProperties;
import com.zhicore.message.infrastructure.push.MessagePushDispatchService;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import com.zhicore.message.infrastructure.repository.po.MessageOutboxTaskPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消息侧 Outbox 派发器。
 */
@Slf4j
@Component
public class MessageOutboxDispatcher extends ClaimBasedDispatcher<MessageOutboxTaskPO> {

    private final MessageOutboxTaskMapper messageOutboxTaskMapper;
    private final MessageOutboxProperties properties;
    private final MessagePushDispatchService messagePushDispatchService;
    private final ImMessageBridgeService imMessageBridgeService;

    public MessageOutboxDispatcher(MessageOutboxTaskMapper messageOutboxTaskMapper,
                                   MessageOutboxProperties properties,
                                   MessagePushDispatchService messagePushDispatchService,
                                   ImMessageBridgeService imMessageBridgeService,
                                   @Qualifier("messageOutboxExecutor") TaskExecutor taskExecutor,
                                   TransactionOperations transactionOperations) {
        super(
                "message-outbox",
                properties.getWorkerCount(),
                Duration.ofSeconds(properties.getClaimTimeoutSeconds()),
                taskExecutor,
                transactionOperations
        );
        this.messageOutboxTaskMapper = messageOutboxTaskMapper;
        this.properties = properties;
        this.messagePushDispatchService = messagePushDispatchService;
        this.imMessageBridgeService = imMessageBridgeService;
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
    protected List<MessageOutboxTaskPO> claimBatch(String workerId, int limit, Duration claimTimeout) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime reclaimBefore = now.minus(claimTimeout);
        return messageOutboxTaskMapper.claimDispatchable(now, reclaimBefore, workerId, limit);
    }

    @Override
    protected void handleClaimed(MessageOutboxTaskPO task) {
        MessageOutboxTaskType taskType = MessageOutboxTaskType.valueOf(task.getTaskType());
        switch (taskType) {
            case MESSAGE_PUSH -> {
                MessageSentPublishRequest request = JsonUtils.fromJson(task.getPayload(), MessageSentPublishRequest.class);
                messagePushDispatchService.dispatchSentMessage(request);
            }
            case MESSAGE_SENT_IM -> {
                MessageSentPublishRequest request = JsonUtils.fromJson(task.getPayload(), MessageSentPublishRequest.class);
                imMessageBridgeService.syncSentMessage(request);
            }
            case MESSAGE_RECALL_IM -> {
                MessageRecallSyncRequest request = JsonUtils.fromJson(task.getPayload(), MessageRecallSyncRequest.class);
                imMessageBridgeService.syncRecallMessage(request);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        task.setStatus(MessageOutboxTaskStatus.SUCCEEDED.name());
        task.setDispatchedAt(now);
        task.setUpdatedAt(now);
        task.setClaimedAt(null);
        task.setClaimedBy(null);
        task.setLastError(null);
        messageOutboxTaskMapper.updateById(task);
    }

    @Override
    protected void handleFailure(MessageOutboxTaskPO task, Exception exception) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retryCount++;
        OffsetDateTime now = OffsetDateTime.now();
        task.setRetryCount(retryCount);
        task.setUpdatedAt(now);
        task.setLastError(exception.getMessage());
        task.setClaimedAt(null);
        task.setClaimedBy(null);

        if (retryCount >= properties.getMaxRetry()) {
            task.setStatus(MessageOutboxTaskStatus.DEAD.name());
            task.setNextAttemptAt(now);
            log.error("Message outbox task dead after retries: taskKey={}, taskType={}",
                    task.getTaskKey(), task.getTaskType(), exception);
        } else {
            task.setStatus(MessageOutboxTaskStatus.FAILED.name());
            task.setNextAttemptAt(now.plusSeconds(backoffSeconds(retryCount)));
            log.warn("Message outbox task failed, will retry: taskKey={}, retryCount={}",
                    task.getTaskKey(), retryCount, exception);
        }

        messageOutboxTaskMapper.updateById(task);
    }

    private long backoffSeconds(int retryCount) {
        long value = 1L << Math.min(retryCount - 1, 20);
        return Math.min(value, properties.getMaxBackoffSeconds());
    }
}
