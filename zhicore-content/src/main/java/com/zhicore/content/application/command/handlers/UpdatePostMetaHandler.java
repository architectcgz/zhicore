package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.UpdatePostMetaCommand;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.event.PostMetadataUpdatedEvent;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 更新文章元数据命令处理器
 * 
 * 验证权限和状态，更新文章元数据
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePostMetaHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    private final DomainEventFactory eventFactory;
    
    /**
     * 处理更新元数据命令
     * 
     * @param command 更新命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(UpdatePostMetaCommand command) {
        log.info("Updating post metadata: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException("无权更新此文章：用户不是文章所有者");
        }
        
        // 验证状态
        if (post.getStatus() == PostStatus.DELETED) {
            throw new IllegalStateException("Cannot update deleted post");
        }
        
        // 更新元数据
        post.updateMeta(
                command.getTitle(),
                command.getExcerpt(),
                command.getCoverImage()
        );
        postRepository.update(post);
        log.info("Post metadata updated: postId={}", command.getPostId());
        
        // 失效缓存
        invalidateCache(command.getPostId());
        
        // 发布事件
        PostMetadataUpdatedEvent event = new PostMetadataUpdatedEvent(
                eventFactory.generateEventId(),
                eventFactory.now(),
                command.getPostId(),
                command.getTitle(),
                command.getExcerpt(),
                command.getCoverImage(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostMetadataUpdated event: postId={}", command.getPostId());
    }
    
    /**
     * 失效相关缓存
     * 
     * 使用领域类型 PostId 作为参数，保持领域模型的完整性。
     * 在应用层方法中，我们已经有了 PostId 对象，直接传递可以避免不必要的类型转换。
     * 
     * @param postId 文章 ID（领域类型）
     */
    private void invalidateCache(PostId postId) {
        cacheRepository.delete(PostRedisKeys.detail(postId));
        log.debug("Invalidated cache for post: {}", postId);
    }
}
