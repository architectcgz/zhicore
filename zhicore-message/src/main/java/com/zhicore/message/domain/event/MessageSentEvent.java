package com.zhicore.message.domain.event;

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

    public MessageSentEvent(Long messageId, Long conversationId, Long senderId, 
                            Long receiverId, MessageType messageType, 
                            String contentPreview, LocalDateTime sentAt) {
        super();
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageType = messageType;
        this.contentPreview = contentPreview;
        this.sentAt = sentAt;
    }

    @Override
    public String getTag() {
        return "MESSAGE_SENT";
    }
}
