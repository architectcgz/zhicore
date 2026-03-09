package com.zhicore.message.application.event;

import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.model.MessageType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 事务提交后用于发布 MQ 事件的消息快照。
 */
@Getter
public class MessageSentPublishRequest {

    private final Long messageId;
    private final Long conversationId;
    private final Long senderId;
    private final Long receiverId;
    private final MessageType messageType;
    private final String contentPreview;
    private final LocalDateTime sentAt;

    private MessageSentPublishRequest(Long messageId,
                                      Long conversationId,
                                      Long senderId,
                                      Long receiverId,
                                      MessageType messageType,
                                      String contentPreview,
                                      LocalDateTime sentAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageType = messageType;
        this.contentPreview = contentPreview;
        this.sentAt = sentAt;
    }

    public static MessageSentPublishRequest from(Message message) {
        return new MessageSentPublishRequest(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getReceiverId(),
                message.getType(),
                message.getPreviewContent(100),
                message.getCreatedAt()
        );
    }
}
