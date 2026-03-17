package com.zhicore.message.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.model.MessageType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 事务提交后驱动消息侧外部副作用的消息快照。
 */
@Getter
public class MessageSentPublishRequest {

    private final Long messageId;
    private final Long conversationId;
    private final Long senderId;
    private final Long receiverId;
    private final MessageType messageType;
    private final String content;
    private final String mediaUrl;
    private final String contentPreview;
    private final LocalDateTime sentAt;

    @JsonCreator
    private MessageSentPublishRequest(@JsonProperty("messageId") Long messageId,
                                      @JsonProperty("conversationId") Long conversationId,
                                      @JsonProperty("senderId") Long senderId,
                                      @JsonProperty("receiverId") Long receiverId,
                                      @JsonProperty("messageType") MessageType messageType,
                                      @JsonProperty("content") String content,
                                      @JsonProperty("mediaUrl") String mediaUrl,
                                      @JsonProperty("contentPreview") String contentPreview,
                                      @JsonProperty("sentAt") LocalDateTime sentAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageType = messageType;
        this.content = content;
        this.mediaUrl = mediaUrl;
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
                message.getContent(),
                message.getMediaUrl(),
                message.getPreviewContent(100),
                message.getCreatedAt()
        );
    }
}
