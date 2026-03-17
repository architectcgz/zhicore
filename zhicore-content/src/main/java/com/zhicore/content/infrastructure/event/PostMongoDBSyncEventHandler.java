package com.zhicore.content.infrastructure.event;

import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostMetadataUpdatedEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument;
import com.zhicore.content.infrastructure.persistence.mongo.repository.PostDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文章 MongoDB 同步事件处理器。
 * 基于可重放任务维护 MongoDB 文章读模型。
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
    private final PostRepository postRepository;

    /**
     * 处理文章创建事件。
     *
     * @param event 文章创建事件
     */
    public void handlePostCreated(PostCreatedDomainEvent event) {
        upsertPostDocument(event.getPostId(), "post-created");
    }

    /**
     * 处理文章标签更新事件。
     *
     * @param event 文章标签更新事件
     */
    public void handlePostTagsUpdated(PostTagsUpdatedDomainEvent event) {
        upsertPostDocument(event.getPostId(), "post-tags-updated");
    }

    public void handlePostPublished(PostPublishedDomainEvent event) {
        upsertPostDocument(event.getPostId(), "post-published");
    }

    public void handlePostMetadataUpdated(PostMetadataUpdatedEvent event) {
        upsertPostDocument(event.getPostId(), "post-metadata-updated");
    }

    public void handlePostContentUpdated(PostContentUpdatedEvent event) {
        upsertPostDocument(event.getPostId(), "post-content-updated");
    }

    public void handlePostDeleted(PostDeletedEvent event) {
        deletePostDocument(event.getPostId(), "post-deleted");
    }

    public void handlePostRestored(PostRestoredEvent event) {
        upsertPostDocument(event.getPostId(), "post-restored");
    }

    public void handlePostPurged(PostPurgedEvent event) {
        deletePostDocument(event.getPostId(), "post-purged");
    }

    private void upsertPostDocument(PostId postId, String trigger) {
        log.info("Upserting MongoDB post document: trigger={}, postId={}", trigger, postId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("Post not found for Mongo projection: postId=" + postId));

        Optional<PostDocument> existing = postDocumentRepository.findByPostId(postId.getValue().toString());
        String content = postContentStore.getContent(postId).map(PostBody::getContent).orElse(null);

        PostDocument document = PostDocument.builder()
                .id(existing.map(PostDocument::getId).orElse(null))
                .postId(postId.getValue().toString())
                .title(post.getTitle())
                .content(content)
                .excerpt(post.getExcerpt())
                .authorId(post.getOwnerId().getValue().toString())
                .authorName(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null)
                .tags(resolveTagInfos(post))
                .categoryId(post.getTopicId() != null ? post.getTopicId().getValue().toString() : null)
                .categoryName(null)
                .status(post.getStatus().name())
                .viewCount(existing.map(PostDocument::getViewCount).orElse(0))
                .likeCount(existing.map(PostDocument::getLikeCount).orElse(0))
                .commentCount(existing.map(PostDocument::getCommentCount).orElse(0))
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();

        postDocumentRepository.save(document);
    }

    private void deletePostDocument(PostId postId, String trigger) {
        log.info("Deleting MongoDB post document: trigger={}, postId={}", trigger, postId);
        postDocumentRepository.deleteByPostId(postId.getValue().toString());
    }

    private List<PostDocument.TagInfo> resolveTagInfos(Post post) {
        if (post.getTagIds() == null || post.getTagIds().isEmpty()) {
            return List.of();
        }

        List<Long> tagIds = post.getTagIds().stream()
                .map(tagId -> tagId.getValue())
                .collect(Collectors.toList());

        List<Tag> tags = tagRepository.findByIdIn(tagIds);
        List<PostDocument.TagInfo> tagInfos = new ArrayList<>();
        for (Tag tag : tags) {
            tagInfos.add(PostDocument.TagInfo.builder()
                    .id(tag.getId().toString())
                    .name(tag.getName())
                    .slug(tag.getSlug())
                    .build());
        }
        return tagInfos;
    }
}

