package com.zhicore.content.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.integration.messaging.comment.CommentDeletedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评论删除事件消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_COMMENT_EVENTS,
        consumerGroup = "zhicore-content-comment-deleted-consumer",
        selectorExpression = TopicConstants.TAG_COMMENT_DELETED
)
public class CommentDeletedConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final ConsumedEventRepository consumedEventRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostRepository postRepository;
    private final PostCacheInvalidationStore postCacheInvalidationStore;

    @Override
    @Transactional
    public void onMessage(String message) {
        try {
            CommentDeletedIntegrationEvent event = objectMapper.readValue(message, CommentDeletedIntegrationEvent.class);
            if (!consumedEventRepository.tryInsert(
                    event.getEventId(),
                    "CommentDeleted",
                    "zhicore-content-comment-deleted-consumer")) {
                return;
            }

            PostId postId = PostId.of(event.getPostId());
            postStatsRepository.decrementCommentCount(postId);
            invalidateReadCaches(postId);
            log.info("Comment deleted stats applied: eventId={}, postId={}, commentId={}",
                    event.getEventId(), event.getPostId(), event.getCommentId());
        } catch (Exception e) {
            log.error("Failed to consume comment deleted event: {}", message, e);
            throw new RuntimeException("Failed to consume comment deleted event", e);
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
