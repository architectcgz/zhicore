package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.event.post.PostUpdatedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.search.domain.repository.PostSearchRepository;
import com.zhicore.search.infrastructure.feign.PostServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 文章更新索引消费者
 * 
 * 消费 PostUpdatedEvent 事件，更新 Elasticsearch 中的文章索引
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_UPDATED,
    consumerGroup = SearchConsumerGroups.POST_UPDATED_CONSUMER
)
public class PostUpdatedSearchConsumer extends AbstractEventConsumer<PostUpdatedEvent> {

    private final PostSearchRepository postSearchRepository;
    private final PostServiceClient postServiceClient;

    public PostUpdatedSearchConsumer(StatefulIdempotentHandler idempotentHandler,
                                     PostSearchRepository postSearchRepository,
                                     PostServiceClient postServiceClient) {
        super(idempotentHandler, PostUpdatedEvent.class);
        this.postSearchRepository = postSearchRepository;
        this.postServiceClient = postServiceClient;
    }

    @Override
    protected void doHandle(PostUpdatedEvent event) {
        Long postId = event.getPostId();
        log.info("Processing post updated event for index update: postId={}", postId);

        try {
            // 检查文章是否已索引（Elasticsearch 使用 String ID）
            String postIdStr = String.valueOf(postId);
            if (postSearchRepository.findById(postIdStr).isEmpty()) {
                var post = PostIndexingSupport.loadPostDetail(postServiceClient, postId, "重建");
                if (post.isEmpty()) {
                    log.info("Post missing during index rebuild, skip update: postId={}", postId);
                    return;
                }

                postSearchRepository.index(PostIndexingSupport.toDocument(post.get()));
                log.info("Index rebuilt from source of truth after missing document: postId={}", postId);
                return;
            }

            // 部分更新索引（不包含标签，标签由 PostTagsUpdatedEvent 单独处理）
            postSearchRepository.partialUpdate(
                postIdStr,
                event.getTitle(),
                event.getContent(),
                event.getExcerpt()
            );

            log.info("Successfully updated post index: postId={}", postId);
        } catch (Exception e) {
            log.error("Failed to update post index: postId={}", postId, e);
            throw e;
        }
    }
}
