package com.zhicore.content.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评论创建事件消费者。
 *
 * 将评论服务 outbox 投递的 create 事件折算到内容服务 post_stats.comment_count，
 * 并同步失效文章详情与列表缓存，避免详情页长期读取旧统计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_COMMENT_EVENTS,
        consumerGroup = "zhicore-content-comment-created-consumer",
        selectorExpression = TopicConstants.TAG_COMMENT_CREATED
)
public class CommentCreatedConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final ConsumedEventRepository consumedEventRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostRepository postRepository;
    private final PostCacheInvalidationStore postCacheInvalidationStore;

    @Override
    @Transactional
    public void onMessage(String message) {
        try {
            CommentCreatedIntegrationEvent event = objectMapper.readValue(message, CommentCreatedIntegrationEvent.class);
            if (!consumedEventRepository.tryInsert(
                    event.getEventId(),
                    "CommentCreated",
                    "zhicore-content-comment-created-consumer")) {
                return;
            }

            PostId postId = PostId.of(event.getPostId());
            postStatsRepository.incrementCommentCount(postId);
            invalidateReadCaches(postId);
            log.info("Comment created stats applied: eventId={}, postId={}, commentId={}",
                    event.getEventId(), event.getPostId(), event.getCommentId());
        } catch (Exception e) {
            log.error("Failed to consume comment created event: {}", message, e);
            throw new RuntimeException("Failed to consume comment created event", e);
        }
    }

    private void invalidateReadCaches(PostId postId) {
        postCacheInvalidationStore.evictDetail(postId);
        postCacheInvalidationStore.evictStats(postId);
        postCacheInvalidationStore.evictLatestList();
        postRepository.findById(postId.getValue()).ifPresent(post -> {
            postCacheInvalidationStore.evictAuthorLists(post.getOwnerId());
            postCacheInvalidationStore.evictTagLists(post.getTagIds());
        });
    }
}
