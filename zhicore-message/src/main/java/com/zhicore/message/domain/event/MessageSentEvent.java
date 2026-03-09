package com.zhicore.message.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import com.zhicore.message.domain.model.MessageType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 消息发送事件
 *
 * @author ZhiCore Team
 */
@Getter
public class MessageSentEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private final Long messageId;

    /**
     * 会话ID
     */
    private final Long conversationId;

    /**
     * 发送者ID
     */
    private final Long senderId;

    /**
     * 接收者ID
     */
    private final Long receiverId;

    /**
     * 消息类型
     */
    private final MessageType messageType;

    /**
     * 消息内容预览
     */
    private final String contentPreview;

    /**
     * 发送时间
     */
    private final LocalDateTime sentAt;

    @JsonCreator
    public MessageSentEvent(@JsonProperty("eventId") String eventId,
                            @JsonProperty("occurredAt") LocalDateTime occurredAt,
                            @JsonProperty("messageId") Long messageId,
                            @JsonProperty("conversationId") Long conversationId,
                            @JsonProperty("senderId") Long senderId,
                            @JsonProperty("receiverId") Long receiverId,
                            @JsonProperty("messageType") MessageType messageType,
                            @JsonProperty("contentPreview") String contentPreview,
                            @JsonProperty("sentAt") LocalDateTime sentAt) {
        super(eventId, occurredAt);
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageType = messageType;
        this.contentPreview = contentPreview;
        this.sentAt = sentAt;
    }

    public MessageSentEvent(Long messageId, Long conversationId, Long senderId, 
                            Long receiverId, MessageType messageType, 
                            String contentPreview, LocalDateTime sentAt) {
        this(null, null, messageId, conversationId, senderId, receiverId, messageType, contentPreview, sentAt);
    }

    @Override
    public String getTag() {
        return "MESSAGE_SENT";
    }
}
