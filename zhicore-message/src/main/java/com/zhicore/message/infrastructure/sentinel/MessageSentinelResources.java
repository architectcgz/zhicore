package com.zhicore.message.infrastructure.sentinel;

/**
 * 消息服务 Sentinel 方法级资源常量。
 */
public final class MessageSentinelResources {

    private MessageSentinelResources() {
    }

    public static final String GET_CONVERSATION_LIST = "message:getConversationList";
    public static final String GET_CONVERSATION = "message:getConversation";
    public static final String GET_CONVERSATION_BY_USER = "message:getConversationByUser";
    public static final String GET_CONVERSATION_COUNT = "message:getConversationCount";
    public static final String GET_MESSAGE_HISTORY = "message:getMessageHistory";
    public static final String GET_UNREAD_COUNT = "message:getUnreadCount";
}
