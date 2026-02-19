package com.blog.search.infrastructure.mq;

import com.blog.api.client.PostServiceClient;
import com.blog.api.dto.post.PostDetailDTO;
import com.blog.api.event.post.PostTagsUpdatedEvent;
import com.blog.common.mq.AbstractEventConsumer;
import com.blog.common.mq.StatefulIdempotentHandler;
import com.blog.common.mq.TopicConstants;
import com.blog.common.result.ApiResponse;
import com.blog.search.domain.model.PostDocument;
import com.blog.search.infrastructure.elasticsearch.PostSearchRepositoryImpl;
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
 * @author Blog Team
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
            // 从文章服务获取完整文章信息（包含标签详情）
            ApiResponse<PostDetailDTO> response = postServiceClient.getPostById(postId);
            
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("Failed to get post details for tag update: postId={}", postId);
                return;
            }

            PostDetailDTO post = response.getData();

            // 构建标签信息列表
            List<PostDocument.TagInfo> tagInfos = null;
            if (post.getTags() != null && !post.getTags().isEmpty()) {
                tagInfos = post.getTags().stream()
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
