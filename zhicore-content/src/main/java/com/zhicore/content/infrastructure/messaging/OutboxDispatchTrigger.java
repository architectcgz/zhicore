package com.zhicore.content.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Outbox 派发触发器。
 *
 * <p>避免业务服务直接依赖条件化创建的 {@link OutboxEventDispatcher}。
 * 在未启用 RocketMQTemplate 的环境下，该触发器会自动降级为空操作。
 */
@Component
@RequiredArgsConstructor
public class OutboxDispatchTrigger {

    private final ObjectProvider<OutboxEventDispatcher> dispatcherProvider;

    public void signal() {
        dispatcherProvider.ifAvailable(OutboxEventDispatcher::signal);
    }
}
