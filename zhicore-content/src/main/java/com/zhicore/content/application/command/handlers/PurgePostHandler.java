package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.PurgePostCommand;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物理删除文章命令处理器
 * 
 * 彻底删除文章（包括 PostgreSQL 和 MongoDB 中的数据）。
 * 注意：这是不可逆操作，只应在必要时使用。
 * 
 * 权限要求：
 * - 只有管理员可以物理删除未软删除的文章
 * - 文章所有者可以物理删除自己已软删除的文章
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgePostHandler {
    
    private final PostRepository postRepository;
    private final PostContentStore postContentStore;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    
    /**
     * 处理物理删除文章命令
     * 
     * @param command 物理删除命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(PurgePostCommand command) {
        log.info("Purging post: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        validatePermission(post, command.getUserId());
        
        // 删除 MongoDB 内容
        try {
            postContentStore.deleteContent(command.getPostId());
            log.info("Deleted MongoDB content for post: postId={}", command.getPostId());
        } catch (Exception e) {
            log.error("Failed to delete MongoDB content: postId={}", command.getPostId(), e);
            throw new RuntimeException("删除文章内容失败", e);
        }
        
        // 删除 PostgreSQL 记录
        postRepository.delete(command.getPostId());
        log.info("Deleted PostgreSQL record for post: postId={}", command.getPostId());
        
        // 清除所有相关缓存
        clearAllCache(command.getPostId(), post);
        
        // 发布事件
        PostPurgedEvent event = new PostPurgedEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                java.time.Instant.now(),
                command.getPostId()
        );
        eventPublisher.publish(event);
        log.info("Published PostPurged event: postId={}", command.getPostId());
    }
    
    /**
     * 验证权限
     * 
     * 权限规则：
     * 1. 如果文章已软删除，文章所有者可以物理删除
     * 2. 如果文章未软删除，只有管理员可以物理删除（需要实现管理员检查）
     * 
     * @param post 文章对象
     * @param userId 用户 ID
     */
    private void validatePermission(Post post, UserId userId) {
        // 检查文章是否已软删除
        if (!post.isDeleted()) {
            // TODO: 实现管理员权限检查
            // 当前简化实现：未软删除的文章不允许物理删除
            throw new IllegalStateException("只能物理删除已软删除的文章");
        }
        
        // 检查是否为文章所有者
        if (!post.isOwnedBy(userId)) {
            throw new PostOwnershipException("无权删除此文章：用户不是文章所有者");
        }
    }
    
    /**
     * 清除所有相关缓存
     * 
     * 包括详情缓存、列表缓存、统计缓存和标签缓存
     * 
     * 使用领域类型 PostId 作为参数，保持领域模型的完整性。
     * 在应用层方法中，我们已经有了 PostId 对象，直接传递可以避免不必要的类型转换。
     * 
     * @param postId 文章 ID（领域类型）
     * @param post 文章对象
     */
    private void clearAllCache(PostId postId, Post post) {
        // 使用 deletePattern 删除所有相关缓存
        cacheRepository.deletePattern(PostRedisKeys.allRelatedPattern(postId));
        
        // 删除详情缓存
        cacheRepository.delete(PostRedisKeys.detail(postId));
        
        // 删除列表缓存
        cacheRepository.delete(
                PostRedisKeys.listLatest(),
                PostRedisKeys.listAuthor(post.getOwnerId())
        );
        
        // 删除统计缓存
        cacheRepository.delete(
                PostRedisKeys.viewCount(postId),
                PostRedisKeys.likeCount(postId),
                PostRedisKeys.commentCount(postId),
                PostRedisKeys.favoriteCount(postId)
        );
        
        log.debug("Cleared all cache for post: {}", postId);
    }
}
