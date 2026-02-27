package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.UnpublishPostCommand;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostUnpublishedEvent;
import com.zhicore.content.domain.exception.PostErrorMessages;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 撤回文章命令处理器
 * 
 * 验证文章状态并撤回已发布的文章
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnpublishPostHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    
    /**
     * 处理撤回文章命令
     * 
     * @param command 撤回命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(UnpublishPostCommand command) {
        log.info("Unpublishing post: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException(PostErrorMessages.NOT_OWNER_UNPUBLISH);
        }
        
        // 验证状态
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new IllegalStateException(PostErrorMessages.NOT_PUBLISHED_STATUS + post.getStatus());
        }
        
        // 撤回
        post.unpublish();
        postRepository.update(post);
        log.info("Post unpublished: postId={}", command.getPostId());
        
        // 失效缓存
        invalidateCache(command.getPostId(), post);
        
        // 发布事件
        PostUnpublishedEvent event = new PostUnpublishedEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                command.getPostId(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostUnpublished event: postId={}", command.getPostId());
    }
    
    /**
     * 失效相关缓存
     * 
     * 使用领域类型 PostId 作为参数，保持领域模型的完整性。
     * 在应用层方法中，我们已经有了 PostId 对象，直接传递可以避免不必要的类型转换。
     * 
     * @param postId 文章 ID（领域类型）
     * @param post 文章对象
     */
    private void invalidateCache(PostId postId, Post post) {
        cacheRepository.delete(PostRedisKeys.detail(postId));
        cacheRepository.deletePattern(PostRedisKeys.listLatestPattern());
        cacheRepository.deletePattern(PostRedisKeys.listAuthorPattern(post.getOwnerId()));
        log.debug("Invalidated cache for unpublished post: {}", postId);
    }
}
