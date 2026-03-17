package com.zhicore.message.infrastructure.mq;

/**
 * 消息 Outbox 任务类型。
 */
public enum MessageOutboxTaskType {
    MESSAGE_PUSH,
    MESSAGE_SENT_IM,
    MESSAGE_RECALL_IM
}
