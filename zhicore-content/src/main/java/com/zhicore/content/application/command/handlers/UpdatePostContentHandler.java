package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.UpdatePostContentCommand;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.*;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 更新文章内容命令处理器
 * 
 * 验证权限和状态，更新文章内容到 MongoDB，更新 PostgreSQL 的 updatedAt 时间戳。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePostContentHandler {
    
    private final PostRepository postRepository;
    private final PostContentStore postContentStore;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    private final DomainEventFactory eventFactory;
    
    /**
     * 处理更新内容命令
     * 
     * @param command 更新命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(UpdatePostContentCommand command) {
        log.info("Updating post content: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 1. 加载文章（从 PostgreSQL）
        Post post = postRepository.load(command.getPostId());
        
        // 2. 验证权限（用户是否拥有文章）
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException("无权更新此文章内容：用户不是文章所有者");
        }
        
        // 3. 验证状态（不能更新已删除的文章）
        if (post.getStatus() == PostStatus.DELETED) {
            throw new IllegalStateException("Cannot update deleted post");
        }
        
        // 4. 更新 MongoDB 内容
        ContentType contentType = command.getContentTypeOrDefault();
        PostBody body = PostBody.create(command.getPostId(), command.getContent(), contentType);
        
        try {
            postContentStore.saveContent(command.getPostId(), body);
            log.info("Post content updated in MongoDB: postId={}", command.getPostId());
        } catch (Exception e) {
            log.error("Failed to update post content in MongoDB: postId={}", command.getPostId(), e);
            throw new RuntimeException("Failed to update post content", e);
        }
        
        // 5. 更新 PostgreSQL 的 updatedAt 时间戳
        post.touch();
        postRepository.update(post);
        log.info("Post updatedAt timestamp updated in PostgreSQL: postId={}", command.getPostId());
        
        // 6. 失效相关缓存
        invalidateCache(command.getPostId());
        
        // 7. 发布 PostContentUpdated 事件
        PostContentUpdatedEvent event = new PostContentUpdatedEvent(
                eventFactory.generateEventId(),
                eventFactory.now(),
                command.getPostId(),
                command.getContent(),
                contentType.getValue(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostContentUpdated event: postId={}", command.getPostId());
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
        // 失效详情缓存
        cacheRepository.delete(PostRedisKeys.detail(postId));
        
        // 失效内容缓存
        cacheRepository.delete(PostRedisKeys.content(postId));
        
        log.debug("Invalidated cache for post: {}", postId);
    }
}
