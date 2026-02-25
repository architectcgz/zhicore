package com.zhicore.content.application.workflow;

import com.zhicore.content.application.command.commands.CreatePostCommand;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.exception.CreatePostFailedException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.WriteState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建草稿工作流
 *
 * 仅负责领域状态迁移与持久化，不负责事件发布。
 * 事件由应用服务统一发布（领域事件 + Outbox 集成事件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateDraftWorkflow {

    private final PostRepository postRepository;
    private final PostContentStore contentStore;
    private final DomainEventFactory domainEventFactory;

    /**
     * 在现有事务中执行创建草稿流程。
     *
     * @param command 创建命令
     * @return 执行结果（文章ID + 领域事件）
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ExecutionResult execute(CreatePostCommand command) {
        PostId postId = command.getPostId();

        try {
            Post post = Post.createDraft(
                    command.getPostId(),
                    command.getOwnerId(),
                    command.getTitle()
            );
            post.updateMeta(command.getTitle(), command.getExcerpt(), command.getCoverImage());
            if (command.getOwnerSnapshot() != null) {
                post.setOwnerSnapshot(command.getOwnerSnapshot());
            }
            if (command.getTagIds() != null && !command.getTagIds().isEmpty()) {
                post.updateTags(command.getTagIds());
            }
            if (command.getTopicId() != null) {
                post.setTopic(command.getTopicId());
            }
            post.setWriteState(WriteState.DRAFT_ONLY);
            postRepository.save(post);

            PostBody body = new PostBody(
                    postId,
                    command.getContent(),
                    command.getContentTypeOrDefault(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            contentStore.saveContent(postId, body);

            post.setWriteState(WriteState.CONTENT_SAVED);
            postRepository.update(post);

            // 重要：创建草稿会经历“插入 + 更新”，乐观锁版本号由数据库/插件推进。
            // 为保证 Outbox 事件强约束（aggregateVersion 非空且代表事务提交时刻聚合版本），这里取数据库最新版本号传入事件。
            Long aggregateVersion = postRepository.findById(postId)
                    .map(Post::getVersion)
                    .orElse(0L);
            post.emitCreatedEvent(domainEventFactory, aggregateVersion);

            return new ExecutionResult(postId, new ArrayList<>(post.pullDomainEvents()));
        } catch (Exception ex) {
            // PG 事务会回滚；Mongo 是独立存储，进行 best-effort 清理避免残留
            try {
                contentStore.deleteContent(postId);
            } catch (Exception cleanupEx) {
                log.warn("Create draft cleanup failed: postId={}", postId, cleanupEx);
            }
            throw new CreatePostFailedException("Failed to create draft: postId=" + postId, ex);
        }
    }

    public record ExecutionResult(PostId postId, List<DomainEvent<?>> domainEvents) {}
}
