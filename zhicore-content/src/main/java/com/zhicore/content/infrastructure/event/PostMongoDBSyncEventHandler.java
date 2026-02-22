package com.zhicore.content.infrastructure.event;

import com.zhicore.content.domain.event.PostCreatedEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedEvent;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.mongodb.document.PostDocument;
import com.zhicore.content.infrastructure.mongodb.repository.PostDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    private final PostTagRepository postTagRepository;

    /**
     * 处理文章创建事件
     * 在事务提交后异步执行，将文章信息同步到 MongoDB
     *
     * @param event 文章创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostCreated(PostCreatedEvent event) {
        try {
            log.info("Handling PostCreated event for postId: {}", event.getPostId());

            // 查询标签信息
            List<PostDocument.TagInfo> tagInfos = new ArrayList<>();
            if (event.getTagIds() != null && !event.getTagIds().isEmpty()) {
                List<Long> tagIds = event.getTagIds().stream()
                    .map(Long::parseLong)
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
                .postId(event.getPostId())
                .title(event.getTitle())
                .content(event.getContent())
                .excerpt(event.getExcerpt())
                .authorId(event.getAuthorId())
                .authorName(event.getAuthorName())
                .tags(tagInfos)
                .categoryId(event.getCategoryId())
                .categoryName(event.getCategoryName())
                .status(event.getStatus())
                .viewCount(0L)
                .likeCount(0)
                .commentCount(0)
                .publishedAt(event.getPublishedAt())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getCreatedAt())
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
    public void handlePostTagsUpdated(PostTagsUpdatedEvent event) {
        try {
            log.info("Handling PostTagsUpdated event for postId: {}", event.getPostId());

            // 查询 MongoDB 文档
            PostDocument document = postDocumentRepository.findByPostId(event.getPostId())
                .orElse(null);

            if (document == null) {
                log.warn("PostDocument not found in MongoDB: postId={}", event.getPostId());
                return;
            }

            // 查询新的标签信息
            List<PostDocument.TagInfo> tagInfos = new ArrayList<>();
            if (event.getNewTagIds() != null && !event.getNewTagIds().isEmpty()) {
                List<Long> tagIds = event.getNewTagIds().stream()
                    .map(Long::parseLong)
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
            document.setUpdatedAt(event.getUpdatedAt());

            postDocumentRepository.save(document);
            log.info("Successfully updated post tags in MongoDB: postId={}", event.getPostId());

        } catch (Exception e) {
            log.error("Failed to update post tags in MongoDB: postId={}", event.getPostId(), e);
            // 不抛出异常，避免影响主流程
        }
    }
}
