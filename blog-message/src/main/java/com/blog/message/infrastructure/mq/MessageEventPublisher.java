package com.blog.message.infrastructure.mq;

import com.blog.common.mq.DomainEventPublisher;
import com.blog.common.mq.TopicConstants;
import com.blog.message.domain.event.MessageSentEvent;
import com.blog.message.domain.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息事件发布器
 *
 * @author Blog Team
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
        MessageSentEvent event = new MessageSentEvent(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getReceiverId(),
                message.getType(),
                message.getPreviewContent(100),
                message.getCreatedAt()
        );

        // 使用顺序消息，以 conversationId 作为 shardingKey
        domainEventPublisher.publishOrderly(
                TopicConstants.TOPIC_MESSAGE_EVENTS,
                TopicConstants.TAG_MESSAGE_SENT,
                event,
                String.valueOf(message.getConversationId())
        );

        log.info("Published MessageSentEvent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                message.getId(), message.getConversationId(), message.getSenderId(), message.getReceiverId());
    }
}
