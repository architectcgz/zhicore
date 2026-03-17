package com.zhicore.message.application.port.event;

import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;

/**
 * 应用层事务事件端口。
 *
 * 负责在事务边界内登记需要在提交后处理的消息事件，
 * 不让 application service 直接依赖 Spring 事件总线。
 */
public interface MessageTransactionEventPort {

    void publishMessageSent(MessageSentPublishRequest request);

    void publishMessageRecalled(MessageRecallSyncRequest request);
}
