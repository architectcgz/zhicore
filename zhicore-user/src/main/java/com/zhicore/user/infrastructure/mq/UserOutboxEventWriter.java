package com.zhicore.user.infrastructure.mq;

import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.infrastructure.config.UserOutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Outbox 写入器。
 *
 * <p>负责在业务事务内落库 outbox 事件，并在事务提交后主动唤醒 dispatcher。
 */
@Component
@RequiredArgsConstructor
public class UserOutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionCommitSignal transactionCommitSignal;
    private final UserOutboxDispatcher userOutboxDispatcher;
    private final UserOutboxProperties userOutboxProperties;

    public OutboxEvent save(String topic, String tag, String shardingKey, String payload) {
        OutboxEvent outboxEvent = OutboxEvent.of(topic, tag, shardingKey, payload);
        outboxEvent.setMaxRetries(userOutboxProperties.getMaxRetry());
        outboxEventRepository.save(outboxEvent);
        transactionCommitSignal.afterCommit(userOutboxDispatcher::signal);
        return outboxEvent;
    }
}
