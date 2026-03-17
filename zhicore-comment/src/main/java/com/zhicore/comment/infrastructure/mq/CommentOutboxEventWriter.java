package com.zhicore.comment.infrastructure.mq;

import com.zhicore.comment.domain.model.OutboxEvent;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.config.CommentOutboxProperties;
import com.zhicore.common.tx.TransactionCommitSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 评论服务 outbox 写入器。
 *
 * <p>负责在业务事务内落库 outbox 事件，并在事务提交后主动唤醒 dispatcher。</p>
 */
@Component
@RequiredArgsConstructor
public class CommentOutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionCommitSignal transactionCommitSignal;
    private final CommentOutboxDispatcher commentOutboxDispatcher;
    private final CommentOutboxProperties commentOutboxProperties;

    public OutboxEvent save(String topic, String tag, String shardingKey, String payload) {
        OutboxEvent outboxEvent = OutboxEvent.of(topic, tag, shardingKey, payload);
        outboxEvent.setMaxRetries(commentOutboxProperties.getMaxRetry());
        outboxEventRepository.save(outboxEvent);
        transactionCommitSignal.afterCommit(commentOutboxDispatcher::signal);
        return outboxEvent;
    }
}
