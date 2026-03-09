package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.event.post.PostPublishedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 文章发布索引消费者
 * 
 * 消费 PostPublishedEvent 事件，将文章索引到 Elasticsearch
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_PUBLISHED,
    consumerGroup = SearchConsumerGroups.POST_PUBLISHED_CONSUMER
)
public class PostPublishedSearchConsumer extends AbstractEventConsumer<PostPublishedEvent> {

    private final PostSearchRepository postSearchRepository;
    private final PostServiceClient postServiceClient;

    public PostPublishedSearchConsumer(StatefulIdempotentHandler idempotentHandler,
                                       PostSearchRepository postSearchRepository,
                                       PostServiceClient postServiceClient) {
        super(idempotentHandler, PostPublishedEvent.class);
        this.postSearchRepository = postSearchRepository;
        this.postServiceClient = postServiceClient;
    }

    @Override
    protected void doHandle(PostPublishedEvent event) {
        Long postId = event.getPostId();
        log.info("Processing post published event for indexing: postId={}", postId);

        try {
            var post = PostIndexingSupport.loadPostDetail(postServiceClient, postId, "创建");
            if (post.isEmpty()) {
                log.info("Post missing during publish indexing, skip indexing: postId={}", postId);
                return;
            }

            postSearchRepository.index(PostIndexingSupport.toDocument(post.get()));

            log.info("Successfully indexed post: postId={}, title={}", postId, post.get().getTitle());
        } catch (Exception e) {
            log.error("Failed to index post: postId={}", postId, e);
            throw e;
        }
    }
}
