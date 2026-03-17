package com.zhicore.common.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DomainEventPublisher 测试")
class DomainEventPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Test
    @DisplayName("应该同步发送普通事件")
    void shouldPublishSyncEvent() {
        DomainEventPublisher publisher = new DomainEventPublisher(rocketMQTemplate);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg-1");
        when(rocketMQTemplate.syncSend(eq("topic-a:tag-a"), org.mockito.ArgumentMatchers.<Message<?>>any()))
                .thenReturn(sendResult);

        publisher.publishSync("topic-a", "tag-a", new SampleEvent("evt-1"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq("topic-a:tag-a"), captor.capture());
        assertEquals("{\"id\":\"evt-1\"}", captor.getValue().getPayload());
        assertEquals("SampleEvent", captor.getValue().getHeaders().get("eventType"));
    }

    @Test
    @DisplayName("应该同步发送顺序事件")
    void shouldPublishOrderlySyncEvent() {
        DomainEventPublisher publisher = new DomainEventPublisher(rocketMQTemplate);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg-2");
        when(rocketMQTemplate.syncSendOrderly(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq("aggregate-1")))
                .thenReturn(sendResult);

        publisher.publishOrderlySync("topic-a", "tag-a", new SampleEvent("evt-2"), "aggregate-1");

        verify(rocketMQTemplate).syncSendOrderly(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq("aggregate-1")
        );
    }

    @Test
    @DisplayName("应该同步发送延迟事件")
    void shouldPublishDelayedSyncEvent() {
        DomainEventPublisher publisher = new DomainEventPublisher(rocketMQTemplate);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg-3");
        when(rocketMQTemplate.syncSend(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                anyLong(),
                anyInt()))
                .thenReturn(sendResult);

        publisher.publishDelayedSync("topic-a", "tag-a", new SampleEvent("evt-3"), 5);

        verify(rocketMQTemplate).syncSend(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq(3000L),
                eq(5)
        );
    }

    private record SampleEvent(String id) {
    }
}
