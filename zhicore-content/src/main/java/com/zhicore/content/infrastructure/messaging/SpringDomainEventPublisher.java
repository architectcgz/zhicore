package com.zhicore.content.infrastructure.messaging;

import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.domain.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Spring ApplicationEventPublisher 的领域事件发布器。
 *
 * 仅用于服务内领域事件传播；跨服务事件请使用 IntegrationEventPublisher + Outbox。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        applicationEventPublisher.publishEvent(event);
        log.debug("Published domain event: eventId={}, eventType={}",
            event.getEventId(), event.getClass().getSimpleName());
    }

    @Override
    public void publishBatch(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(this::publish);
    }
}
