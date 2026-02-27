package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.DeletePostCommand;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.exception.PostErrorMessages;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除文章命令处理器
 * 
 * 软删除文章（不物理删除）
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletePostHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    private final DomainEventFactory eventFactory;
    
    /**
     * 处理删除文章命令
     * 
     * @param command 删除命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(DeletePostCommand command) {
        log.info("Deleting post: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException(PostErrorMessages.NOT_OWNER_DELETE);
        }
        
        // 软删除
        post.delete();
        postRepository.update(post);
        log.info("Post deleted (soft): postId={}", command.getPostId());
        
        // 失效所有相关缓存
        invalidateAllCache(command.getPostId(), post);
        
        // 发布事件
        PostDeletedEvent event = new PostDeletedEvent(
                eventFactory.generateEventId(),
                eventFactory.now(),
                command.getPostId(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostDeleted event: postId={}", command.getPostId());
    }
    
    /**
     * 失效所有相关缓存
     * 
     * 包括详情缓存、列表缓存和标签缓存
     * 
     * 使用领域类型参数的原因：
     * 1. 类型安全：编译期检查，避免传递错误的 ID 类型
     * 2. 语义清晰：明确表示这是文章 ID，而不是普通字符串
     * 3. 一致性：与 PostRedisKeys 工具类的设计保持一致
     * 4. 封装性：PostId 封装了 ID 的验证和转换逻辑
     * 
     * @param postId 文章 ID（领域类型）
     * @param post 文章对象
     */
    private void invalidateAllCache(PostId postId, Post post) {
        // 使用 deletePattern 删除所有相关缓存
        cacheRepository.deletePattern(PostRedisKeys.allRelatedPattern(postId));
        
        // 删除列表缓存（分页/size 等维度下为多 key，统一用 pattern 失效）
        cacheRepository.deletePattern(PostRedisKeys.listLatestPattern());
        cacheRepository.deletePattern(PostRedisKeys.listAuthorPattern(post.getOwnerSnapshot().getOwnerId()));
        
        // 删除标签列表缓存（分页/size 等维度下为多 key，统一用 pattern 失效）
        post.getTagIds().forEach(tagId ->
                cacheRepository.deletePattern(PostRedisKeys.listTagPattern(tagId))
        );
        
        log.debug("Invalidated all cache for post: {}", postId);
    }
}
