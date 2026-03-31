package com.zhicore.common.mq;

import com.zhicore.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String DEFAULT_ENV = "default";
    private static final String DEFAULT_CONSUMER_GROUP = "unknown-group";
    private static final String DEFAULT_TOPIC = "unknown-topic";
    private static final Map<Class<?>, ListenerScope> LISTENER_SCOPE_CACHE = new ConcurrentHashMap<>();

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
            String idempotentKey = buildIdempotentKey(eventId);
            
            log.debug("Received event: type={}, eventId={}, idempotentKey={}",
                    eventType.getSimpleName(), eventId, idempotentKey);
            
            // 幂等性处理
            boolean processed = idempotentHandler.handleIdempotent(idempotentKey, () -> {
                doHandle(finalEvent);
            });
            
            if (!processed) {
                log.debug("Event already processed or being processed: eventId={}, idempotentKey={}",
                        eventId, idempotentKey);
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
            Class<?> current = event.getClass();
            while (current != null) {
                try {
                    var field = current.getDeclaredField("eventId");
                    field.setAccessible(true);
                    Object value = field.get(event);
                    if (value instanceof String eventId && !eventId.isBlank()) {
                        return eventId;
                    }
                    break;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract eventId via reflection, fallback to hashCode: type={}",
                    event.getClass().getSimpleName(), e);
        }

        // 如果没有 eventId 字段，使用对象的 hashCode
        return String.valueOf(event.hashCode());
    }

    /**
     * 构建幂等键，格式：{env}:{consumerGroup}:{topic}:{eventId}
     *
     * <p>避免多个服务共享 Redis 时，仅用 eventId 导致跨服务误判“已消费”。
     *
     * @param eventId 事件ID
     * @return 幂等键
     */
    protected String buildIdempotentKey(String eventId) {
        Class<?> userClass = ClassUtils.getUserClass(getClass());
        ListenerScope scope = LISTENER_SCOPE_CACHE.computeIfAbsent(userClass, AbstractEventConsumer::resolveListenerScope);
        return resolveEnv() + ":" + scope.consumerGroup() + ":" + scope.topic() + ":" + eventId;
    }

    private static ListenerScope resolveListenerScope(Class<?> consumerClass) {
        RocketMQMessageListener listener = consumerClass.getAnnotation(RocketMQMessageListener.class);
        if (listener == null) {
            log.warn("RocketMQMessageListener annotation missing, fallback idempotent scope: consumer={}",
                    consumerClass.getName());
            return new ListenerScope(DEFAULT_CONSUMER_GROUP, DEFAULT_TOPIC);
        }

        String consumerGroup = normalizeSegment(listener.consumerGroup(), DEFAULT_CONSUMER_GROUP);
        String topic = normalizeSegment(listener.topic(), DEFAULT_TOPIC);
        return new ListenerScope(consumerGroup, topic);
    }

    private String resolveEnv() {
        String env = normalizeSegment(System.getProperty("APP_ENV"), null);
        if (env != null) {
            return env;
        }

        env = normalizeSegment(System.getenv("APP_ENV"), null);
        if (env != null) {
            return env;
        }

        env = primaryProfile(System.getProperty("spring.profiles.active"));
        if (env != null) {
            return env;
        }

        env = primaryProfile(System.getenv("SPRING_PROFILES_ACTIVE"));
        if (env != null) {
            return env;
        }

        return DEFAULT_ENV;
    }

    private static String primaryProfile(String profiles) {
        String normalized = normalizeSegment(profiles, null);
        if (normalized == null) {
            return null;
        }
        int commaIndex = normalized.indexOf(',');
        if (commaIndex <= 0) {
            return normalized;
        }
        return normalizeSegment(normalized.substring(0, commaIndex), null);
    }

    private static String normalizeSegment(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        return normalized.replace(':', '_');
    }

    private record ListenerScope(String consumerGroup, String topic) {
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
