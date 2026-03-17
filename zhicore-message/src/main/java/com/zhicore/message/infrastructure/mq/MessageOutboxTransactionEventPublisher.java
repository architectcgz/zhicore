package com.zhicore.message.infrastructure.mq;

import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.port.event.MessageTransactionEventPort;
import com.zhicore.message.infrastructure.config.ImBridgeProperties;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import com.zhicore.message.infrastructure.repository.po.MessageOutboxTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 事务内将消息侧外部副作用写入 Outbox。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageOutboxTransactionEventPublisher implements MessageTransactionEventPort {

    private final MessageOutboxTaskMapper messageOutboxTaskMapper;
    private final ImBridgeProperties imBridgeProperties;
    private final TransactionCommitSignal transactionCommitSignal;
    private final MessageOutboxDispatcher messageOutboxDispatcher;

    @Override
    public void publishMessageSent(MessageSentPublishRequest request) {
        insertTask(taskKey(MessageOutboxTaskType.MESSAGE_PUSH, request.getMessageId()),
                MessageOutboxTaskType.MESSAGE_PUSH,
                request.getConversationId(),
                JsonUtils.toJson(request));

        if (imBridgeProperties.isEnabled()) {
            insertTask(taskKey(MessageOutboxTaskType.MESSAGE_SENT_IM, request.getMessageId()),
                    MessageOutboxTaskType.MESSAGE_SENT_IM,
                    request.getConversationId(),
                    JsonUtils.toJson(request));
        }
    }

    @Override
    public void publishMessageRecalled(MessageRecallSyncRequest request) {
        if (!imBridgeProperties.isEnabled()) {
            return;
        }

        insertTask(taskKey(MessageOutboxTaskType.MESSAGE_RECALL_IM, request.getMessageId()),
                MessageOutboxTaskType.MESSAGE_RECALL_IM,
                resolveAggregateId(request),
                JsonUtils.toJson(request));
    }

    private void insertTask(String taskKey, MessageOutboxTaskType taskType, Long aggregateId, String payload) {
        MessageOutboxTaskPO task = new MessageOutboxTaskPO();
        OffsetDateTime now = OffsetDateTime.now();
        task.setTaskKey(taskKey);
        task.setTaskType(taskType.name());
        task.setAggregateId(aggregateId);
        task.setPayload(payload);
        task.setRetryCount(0);
        task.setStatus(MessageOutboxTaskStatus.PENDING.name());
        task.setNextAttemptAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        messageOutboxTaskMapper.insert(task);
        transactionCommitSignal.afterCommit(messageOutboxDispatcher::dispatch);

        log.debug("Message outbox task created: taskKey={}, taskType={}, aggregateId={}",
                taskKey, taskType, aggregateId);
    }

    private String taskKey(MessageOutboxTaskType taskType, Long messageId) {
        return taskType.name() + ":" + messageId;
    }

    private Long resolveAggregateId(MessageRecallSyncRequest request) {
        return request.getConversationId() != null ? request.getConversationId() : request.getMessageId();
    }
}
