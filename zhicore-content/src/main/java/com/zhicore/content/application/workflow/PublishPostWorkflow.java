package com.zhicore.content.application.workflow;

import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.exception.PublishPostFailedException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 发布文章工作流
 *
 * 仅负责发布状态迁移，不直接发布消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishPostWorkflow {

    private final PostRepository postRepository;
    private final PostContentStore contentStore;
    private final DomainEventFactory domainEventFactory;

    /**
     * 在现有事务中执行发布流程。
     *
     * @param postId 文章 ID
     * @param userId 用户 ID（作者）
     * @return 发布产生的内部事件列表
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<DomainEvent<?>> execute(PostId postId, UserId userId) {
        try {
            Post post = postRepository.load(postId);

            if (!post.isOwnedBy(userId)) {
                throw new PublishPostFailedException("无权发布此文章: postId=" + postId + ", userId=" + userId);
            }
            if (post.getStatus() == PostStatus.DELETED) {
                throw new PublishPostFailedException("不能发布已删除的文章: " + postId);
            }
            if (contentStore.getContent(postId).isEmpty()) {
                throw new PublishPostFailedException("文章内容不存在，无法发布: " + postId);
            }
            if (post.getWriteState() == WriteState.DRAFT_ONLY) {
                throw new PublishPostFailedException("文章内容未保存，无法发布: " + postId);
            }

            post.publish();
            post.setWriteState(WriteState.PUBLISHED);
            postRepository.update(post);

            Long aggregateVersion = postRepository.findById(postId)
                    .map(Post::getVersion)
                    .orElse(0L);
            return List.of(buildPostPublishedEvent(post, aggregateVersion));
        } catch (PublishPostFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new PublishPostFailedException("发布文章失败: " + e.getMessage(), e);
        }
    }

    private PostPublishedDomainEvent buildPostPublishedEvent(Post post, Long aggregateVersion) {
        return new PostPublishedDomainEvent(
                domainEventFactory.generateEventId(),
                domainEventFactory.now(),
                post.getId(),
                post.getPublishedAt().atZone(ZoneId.systemDefault()).toInstant(),
                aggregateVersion != null ? aggregateVersion : 0L
        );
    }
}
