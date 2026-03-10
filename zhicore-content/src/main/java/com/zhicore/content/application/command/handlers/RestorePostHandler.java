package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.RestorePostCommand;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.application.port.cachekey.PostCacheKeyResolver;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.exception.PostErrorMessages;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 恢复文章命令处理器
 * 
 * 恢复已删除的文章
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestorePostHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final CacheStore cacheStore;
    private final PostCacheKeyResolver postCacheKeyResolver;
    
    /**
     * 处理恢复文章命令
     * 
     * @param command 恢复命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(RestorePostCommand command) {
        log.info("Restoring post: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException(PostErrorMessages.NOT_OWNER_RESTORE);
        }
        
        // 恢复
        post.restore();
        postRepository.update(post);
        log.info("Post restored: postId={}, newStatus={}", command.getPostId(), post.getStatus());
        
        // 失效所有相关缓存
        invalidateAllCache(command.getPostId(), post);
        
        // 发布事件
        PostRestoredEvent event = new PostRestoredEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                java.time.Instant.now(),
                command.getPostId(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostRestored event: postId={}", command.getPostId());
    }
    
    /**
     * 失效所有相关缓存
     * 
     * 包括详情缓存、列表缓存和标签缓存
     * 
     * 使用领域类型作为参数的原因：
     * 1. 类型安全：编译期检查，避免传递错误的 ID 类型
     * 2. 语义清晰：明确表示这是文章 ID，而不是其他类型的 ID
     * 3. 一致性：与 PostRedisKeys 的方法签名保持一致
     * 
     * @param postId 文章 ID（领域类型）
     * @param post 文章对象
     */
    private void invalidateAllCache(PostId postId, Post post) {
        // 使用 deletePattern 删除所有相关缓存
        cacheStore.deletePattern(postCacheKeyResolver.allRelatedPattern(postId));
        
        // 删除列表缓存（分页/size 等维度下为多 key，统一用 pattern 失效）
        cacheStore.deletePattern(postCacheKeyResolver.listLatestPattern());
        cacheStore.deletePattern(postCacheKeyResolver.listAuthorPattern(post.getOwnerSnapshot().getOwnerId()));
        
        // 删除标签列表缓存（分页/size 等维度下为多 key，统一用 pattern 失效）
        post.getTagIds().forEach(tag ->
                cacheStore.deletePattern(postCacheKeyResolver.listTagPattern(tag))
        );
        
        log.debug("Invalidated all cache for post: {}", postId.getValue());
    }
}
