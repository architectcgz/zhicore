package com.zhicore.message.infrastructure.mq;

import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.common.mq.DomainEventPublisher;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.message.domain.event.MessageSentEvent;
import com.zhicore.message.domain.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息事件发布器
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 发布消息发送事件
     * 使用顺序消息，以 conversationId 作为 shardingKey 保证同一会话消息顺序
     *
     * @param message 消息
     */
    public void publishMessageSent(Message message) {
        publishMessageSent(MessageSentPublishRequest.from(message));
    }

    /**
     * 发布消息发送事件快照。
     *
     * @param request 事务提交后保留的消息快照
     */
    public void publishMessageSent(MessageSentPublishRequest request) {
        MessageSentEvent event = new MessageSentEvent(
                request.getMessageId(),
                request.getConversationId(),
                request.getSenderId(),
                request.getReceiverId(),
                request.getMessageType(),
                request.getContentPreview(),
                request.getSentAt()
        );

        // 使用顺序消息，以 conversationId 作为 shardingKey
        domainEventPublisher.publishOrderly(
                TopicConstants.TOPIC_MESSAGE_EVENTS,
                TopicConstants.TAG_MESSAGE_SENT,
                event,
                String.valueOf(request.getConversationId())
        );

        log.info("Published MessageSentEvent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                request.getMessageId(), request.getConversationId(), request.getSenderId(), request.getReceiverId());
    }
}
