package com.zhicore.message.application.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.message.domain.model.Message;
import lombok.Getter;

/**
 * 事务提交后用于同步 IM 撤回的消息快照。
 */
@Getter
public class MessageRecallSyncRequest {

    private final Long messageId;
    private final Long conversationId;
    private final Long senderId;

    @JsonCreator
    public MessageRecallSyncRequest(@JsonProperty("messageId") Long messageId,
                                    @JsonProperty("conversationId") Long conversationId,
                                    @JsonProperty("senderId") Long senderId) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
    }

    public static MessageRecallSyncRequest from(Message message) {
        return new MessageRecallSyncRequest(
                message.getId(),
                message.getConversationId(),
                message.getSenderId()
        );
    }
}
