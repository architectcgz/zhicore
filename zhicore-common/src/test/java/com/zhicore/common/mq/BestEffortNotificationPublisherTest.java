package com.zhicore.common.mq;

import org.apache.rocketmq.client.producer.SendCallback;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BestEffortNotificationPublisher 测试")
class BestEffortNotificationPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Test
    @DisplayName("应该异步发送普通通知")
    void shouldPublishAsyncNotification() {
        BestEffortNotificationPublisher publisher = new BestEffortNotificationPublisher(rocketMQTemplate);
        doNothing().when(rocketMQTemplate).asyncSend(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                org.mockito.ArgumentMatchers.any(SendCallback.class)
        );

        publisher.publishAsync("topic-a", "tag-a", new SampleNotification("notice-1"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).asyncSend(
                eq("topic-a:tag-a"),
                captor.capture(),
                org.mockito.ArgumentMatchers.any(SendCallback.class)
        );
        assertEquals("{\"id\":\"notice-1\"}", captor.getValue().getPayload());
        assertEquals("SampleNotification", captor.getValue().getHeaders().get("eventType"));
    }

    @Test
    @DisplayName("应该异步发送顺序通知")
    void shouldPublishOrderlyAsyncNotification() {
        BestEffortNotificationPublisher publisher = new BestEffortNotificationPublisher(rocketMQTemplate);
        doNothing().when(rocketMQTemplate).asyncSendOrderly(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq("aggregate-1"),
                org.mockito.ArgumentMatchers.any(SendCallback.class)
        );

        publisher.publishOrderlyAsync("topic-a", "tag-a", new SampleNotification("notice-2"), "aggregate-1");

        verify(rocketMQTemplate).asyncSendOrderly(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq("aggregate-1"),
                org.mockito.ArgumentMatchers.any(SendCallback.class)
        );
    }

    @Test
    @DisplayName("应该异步发送延迟通知")
    void shouldPublishDelayedAsyncNotification() {
        BestEffortNotificationPublisher publisher = new BestEffortNotificationPublisher(rocketMQTemplate);
        doNothing().when(rocketMQTemplate).asyncSend(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                org.mockito.ArgumentMatchers.any(SendCallback.class),
                anyLong(),
                anyInt()
        );

        publisher.publishDelayedAsync("topic-a", "tag-a", new SampleNotification("notice-3"), 5);

        verify(rocketMQTemplate).asyncSend(
                eq("topic-a:tag-a"),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                org.mockito.ArgumentMatchers.any(SendCallback.class),
                eq(3000L),
                eq(5)
        );
    }

    private record SampleNotification(String id) {
    }
}
