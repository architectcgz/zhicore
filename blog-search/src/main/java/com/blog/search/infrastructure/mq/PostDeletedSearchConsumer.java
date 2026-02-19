package com.blog.search.infrastructure.mq;

import com.blog.api.event.post.PostDeletedEvent;
import com.blog.common.mq.AbstractEventConsumer;
import com.blog.common.mq.StatefulIdempotentHandler;
import com.blog.common.mq.TopicConstants;
import com.blog.search.domain.repository.PostSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 文章删除索引消费者
 * 
 * 消费 PostDeletedEvent 事件，从 Elasticsearch 中删除文章索引
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_DELETED,
    consumerGroup = SearchConsumerGroups.POST_DELETED_CONSUMER
)
public class PostDeletedSearchConsumer extends AbstractEventConsumer<PostDeletedEvent> {

    private final PostSearchRepository postSearchRepository;

    public PostDeletedSearchConsumer(StatefulIdempotentHandler idempotentHandler,
                                     PostSearchRepository postSearchRepository) {
        super(idempotentHandler, PostDeletedEvent.class);
        this.postSearchRepository = postSearchRepository;
    }

    @Override
    protected void doHandle(PostDeletedEvent event) {
        Long postId = event.getPostId();
        log.info("Processing post deleted event for index removal: postId={}", postId);

        try {
            // 从索引中删除文章（Elasticsearch 使用 String ID）
            postSearchRepository.delete(String.valueOf(postId));

            log.info("Successfully deleted post from index: postId={}", postId);
        } catch (Exception e) {
            log.error("Failed to delete post from index: postId={}", postId, e);
            throw e;
        }
    }
}
