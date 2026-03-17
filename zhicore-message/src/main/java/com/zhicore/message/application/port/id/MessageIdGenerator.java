package com.zhicore.message.application.port.id;

/**
 * 消息领域使用的分布式 ID 生成端口。
 */
public interface MessageIdGenerator {

    Long nextId();
}
