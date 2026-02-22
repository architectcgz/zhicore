package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.api.event.post.PostPublishedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.util.List;

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
            // 从文章服务获取完整文章信息（Feign client 使用 Long）
            ApiResponse<PostDetailDTO> response = postServiceClient.getPostById(postId);
            
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("Failed to get post details for indexing: postId={}", postId);
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
                    .collect(java.util.stream.Collectors.toList());
            }

            // 构建文章文档（Elasticsearch 使用 String ID）
            PostDocument document = PostDocument.builder()
                .id(String.valueOf(post.getId()))
                .title(post.getTitle())
                .content(post.getRaw()) // 使用原始内容进行索引
                .excerpt(post.getExcerpt())
                .authorId(post.getAuthor() != null ? String.valueOf(post.getAuthor().getId()) : null)
                .authorName(post.getAuthor() != null ? post.getAuthor().getNickName() : null)
                .tags(tagInfos)
                .categoryName(post.getCategories() != null && !post.getCategories().isEmpty() 
                    ? post.getCategories().get(0) : null)
                .status("PUBLISHED")
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
                .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();

            // 索引文章
            postSearchRepository.index(document);

            log.info("Successfully indexed post: postId={}, title={}", postId, post.getTitle());
        } catch (Exception e) {
            log.error("Failed to index post: postId={}", postId, e);
            throw e;
        }
    }
}
