package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.event.post.PostTagsUpdatedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.infrastructure.elasticsearch.PostSearchRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章标签更新索引消费者
 * 
 * 消费 PostTagsUpdatedEvent 事件，更新 Elasticsearch 中的文章标签信息
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_TAGS_UPDATED,
    consumerGroup = SearchConsumerGroups.POST_TAGS_UPDATED_CONSUMER
)
public class PostTagsUpdatedSearchConsumer extends AbstractEventConsumer<PostTagsUpdatedEvent> {

    private final PostSearchRepositoryImpl postSearchRepository;
    private final PostServiceClient postServiceClient;

    public PostTagsUpdatedSearchConsumer(StatefulIdempotentHandler idempotentHandler,
                                        PostSearchRepositoryImpl postSearchRepository,
                                        PostServiceClient postServiceClient) {
        super(idempotentHandler, PostTagsUpdatedEvent.class);
        this.postSearchRepository = postSearchRepository;
        this.postServiceClient = postServiceClient;
    }

    @Override
    protected void doHandle(PostTagsUpdatedEvent event) {
        Long postId = event.getPostId();
        log.info("Processing post tags updated event for indexing: postId={}", postId);

        try {
            var post = PostIndexingSupport.loadPostDetail(postServiceClient, postId, "更新标签");
            if (post.isEmpty()) {
                log.info("Post missing during tag sync, skip tag update: postId={}", postId);
                return;
            }

            // 构建标签信息列表
            List<PostDocument.TagInfo> tagInfos = null;
            if (post.get().getTags() != null && !post.get().getTags().isEmpty()) {
                tagInfos = post.get().getTags().stream()
                    .map(tag -> PostDocument.TagInfo.builder()
                        .id(String.valueOf(tag.getId()))
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .build())
                    .collect(Collectors.toList());
            }

            // 更新 Elasticsearch 中的标签信息
            postSearchRepository.updateTags(String.valueOf(postId), tagInfos);

            log.info("Successfully updated post tags in index: postId={}, tagCount={}", 
                postId, tagInfos != null ? tagInfos.size() : 0);
        } catch (Exception e) {
            log.error("Failed to update post tags in index: postId={}", postId, e);
            throw e;
        }
    }
}
