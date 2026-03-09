package com.zhicore.message.infrastructure.mq;

import com.zhicore.message.application.event.MessageSentPublishRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后触发消息发送事件投递，避免事务内直接发 MQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSentTransactionListener {

    private final MessageEventPublisher messageEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentPublishRequest request) {
        messageEventPublisher.publishMessageSent(request);
        log.debug("MessageSent event published after commit: messageId={}, conversationId={}",
                request.getMessageId(), request.getConversationId());
    }
}
