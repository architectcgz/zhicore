package com.zhicore.content.infrastructure.mq;

import com.zhicore.api.event.post.PostScheduleExecuteEvent;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.content.application.service.PostApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文章定时发布消费者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = "schedule-execute",
        consumerGroup = "post-schedule-consumer-group"
)
public class PostScheduleConsumer implements RocketMQListener<String> {

    private final PostApplicationService postApplicationService;

    @Override
    public void onMessage(String message) {
        try {
            PostScheduleExecuteEvent event = JsonUtils.fromJson(message, PostScheduleExecuteEvent.class);
            log.info("Received schedule execute event: postId={}", event.getPostId());
            
            postApplicationService.executeScheduledPublish(event.getPostId());
            
        } catch (Exception e) {
            log.error("Failed to process schedule execute event: {}", message, e);
            throw e;
        }
    }
}
