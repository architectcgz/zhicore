package com.zhicore.integration.messaging;

/**
 * 支持延迟投递的集成事件标记接口。
 */
public interface DelayableIntegrationEvent {

    /**
     * RocketMQ 延迟级别（1-18）。
     *
     * @return 延迟级别；null 或 <=0 表示不使用延迟
     */
    Integer getDelayLevel();
}
