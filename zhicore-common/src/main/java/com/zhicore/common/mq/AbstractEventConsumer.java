package com.zhicore.common.mq;

import com.zhicore.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * 事件消费者基类
 * 
 * 提供幂等性处理、异常处理等通用功能
 *
 * @param <T> 事件类型
 * @author ZhiCore Team
 */
@Slf4j
public abstract class AbstractEventConsumer<T> implements RocketMQListener<String> {

    private final StatefulIdempotentHandler idempotentHandler;
    private final Class<T> eventType;

    protected AbstractEventConsumer(StatefulIdempotentHandler idempotentHandler, Class<T> eventType) {
        this.idempotentHandler = idempotentHandler;
        this.eventType = eventType;
    }

    @Override
    public void onMessage(String message) {
        T event = null;
        try {
            event = JsonUtils.fromJson(message, eventType);
            final T finalEvent = event;
            String eventId = extractEventId(event);
            
            log.debug("Received event: type={}, eventId={}", eventType.getSimpleName(), eventId);
            
            // 幂等性处理
            boolean processed = idempotentHandler.handleIdempotent(eventId, () -> {
                doHandle(finalEvent);
            });
            
            if (!processed) {
                log.debug("Event already processed or being processed: eventId={}", eventId);
            }
        } catch (Exception e) {
            log.error("Failed to process event: type={}, message={}", 
                    eventType.getSimpleName(), message, e);
            handleException(event, e);
            throw e; // 重新抛出异常，让 RocketMQ 进行重试
        }
    }

    /**
     * 从事件中提取事件ID
     * 子类可以覆盖此方法以自定义事件ID提取逻辑
     *
     * @param event 事件对象
     * @return 事件ID
     */
    protected String extractEventId(T event) {
        // 默认使用反射获取 eventId 字段
        try {
            var field = event.getClass().getDeclaredField("eventId");
            field.setAccessible(true);
            return (String) field.get(event);
        } catch (Exception e) {
            // 如果没有 eventId 字段，使用对象的 hashCode
            return String.valueOf(event.hashCode());
        }
    }

    /**
     * 处理事件的具体逻辑
     * 子类必须实现此方法
     *
     * @param event 事件对象
     */
    protected abstract void doHandle(T event);

    /**
     * 异常处理钩子
     * 子类可以覆盖此方法以自定义异常处理逻辑
     *
     * @param event 事件对象（可能为null）
     * @param e 异常
     */
    protected void handleException(T event, Exception e) {
        // 默认不做额外处理，子类可以覆盖
    }
}
