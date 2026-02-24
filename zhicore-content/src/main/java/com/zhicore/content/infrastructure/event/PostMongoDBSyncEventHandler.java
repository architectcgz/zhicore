package com.zhicore.content.infrastructure.event;

import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument;
import com.zhicore.content.infrastructure.persistence.mongo.repository.PostDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章 MongoDB 同步事件处理器
 * 监听文章相关事件，同步数据到 MongoDB
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostMongoDBSyncEventHandler {

    private final PostDocumentRepository postDocumentRepository;
    private final TagRepository tagRepository;
    private final PostContentStore postContentStore;

    /**
     * 处理文章创建事件
     * 在事务提交后异步执行，将文章信息同步到 MongoDB
     *
     * @param event 文章创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostCreated(PostCreatedDomainEvent event) {
        try {
            log.info("Handling PostCreated event for postId: {}", event.getPostId());

            String content = postContentStore.getContent(event.getPostId())
                .map(PostBody::getContent)
                .orElse(null);

            // 查询标签信息
            List<PostDocument.TagInfo> tagInfos = new ArrayList<>();
            if (event.getTagIds() != null && !event.getTagIds().isEmpty()) {
                List<Long> tagIds = event.getTagIds().stream()
                    .map(tagId -> tagId.getValue())
                    .collect(Collectors.toList());
                
                List<Tag> tags = tagRepository.findByIdIn(tagIds);
                tagInfos = tags.stream()
                    .map(tag -> PostDocument.TagInfo.builder()
                        .id(tag.getId().toString())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .build())
                    .collect(Collectors.toList());
            }

            // 创建 MongoDB 文档
            PostDocument document = PostDocument.builder()
                .postId(event.getPostId().getValue().toString())
                .title(event.getTitle())
                .content(content)
                .excerpt(event.getExcerpt())
                .authorId(event.getAuthorId().getValue().toString())
                .authorName(event.getAuthorName())
                .tags(tagInfos)
                .categoryId(event.getTopicId() != null ? event.getTopicId().getValue().toString() : null)
                .categoryName(event.getTopicName())
                .status(event.getStatus())
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .publishedAt(toLocalDateTime(event.getPublishedAt()))
                .createdAt(toLocalDateTime(event.getCreatedAt()))
                .updatedAt(toLocalDateTime(event.getCreatedAt()))
                .build();

            postDocumentRepository.save(document);
            log.info("Successfully synced post to MongoDB: postId={}", event.getPostId());

        } catch (Exception e) {
            log.error("Failed to sync post to MongoDB: postId={}", event.getPostId(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 处理文章标签更新事件
     * 在事务提交后异步执行，更新 MongoDB 中的标签信息
     *
     * @param event 文章标签更新事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostTagsUpdated(PostTagsUpdatedDomainEvent event) {
        try {
            log.info("Handling PostTagsUpdated event for postId: {}", event.getPostId());

            // 查询 MongoDB 文档
            PostDocument document = postDocumentRepository.findByPostId(event.getPostId().getValue().toString())
                .orElse(null);

            if (document == null) {
                log.warn("PostDocument not found in MongoDB: postId={}", event.getPostId());
                return;
            }

            // 查询新的标签信息
            List<PostDocument.TagInfo> tagInfos = new ArrayList<>();
            if (event.getNewTagIds() != null && !event.getNewTagIds().isEmpty()) {
                List<Long> tagIds = event.getNewTagIds().stream()
                    .map(tagId -> tagId.getValue())
                    .collect(Collectors.toList());
                
                List<Tag> tags = tagRepository.findByIdIn(tagIds);
                tagInfos = tags.stream()
                    .map(tag -> PostDocument.TagInfo.builder()
                        .id(tag.getId().toString())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .build())
                    .collect(Collectors.toList());
            }

            // 更新标签信息
            document.setTags(tagInfos);
            document.setUpdatedAt(toLocalDateTime(event.getUpdatedAt()));

            postDocumentRepository.save(document);
            log.info("Successfully updated post tags in MongoDB: postId={}", event.getPostId());

        } catch (Exception e) {
            log.error("Failed to update post tags in MongoDB: postId={}", event.getPostId(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }
}

