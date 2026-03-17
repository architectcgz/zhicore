package com.zhicore.content.application.command.handlers;

import com.zhicore.common.context.UserContext;
import com.zhicore.content.application.command.commands.PurgePostCommand;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.exception.PostErrorMessages;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
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
    private final PostCacheInvalidationStore postCacheInvalidationStore;
    
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

        // 审计日志（TASK-03）：操作者、目标文章、操作时间由日志时间戳提供
        log.warn("AUDIT PurgePost: operatorId={}, postId={}, postDeleted={}, isAdmin={}",
                command.getUserId(),
                command.getPostId(),
                post.isDeleted(),
                UserContext.isAdmin());
        
        // 删除 MongoDB 内容
        try {
            postContentStore.deleteContent(command.getPostId());
            log.info("Deleted MongoDB content for post: postId={}", command.getPostId());
        } catch (Exception e) {
            log.error("Failed to delete MongoDB content: postId={}", command.getPostId(), e);
            throw new RuntimeException(PostErrorMessages.DELETE_CONTENT_FAILED, e);
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
                command.getPostId(),
                java.util.Set.copyOf(post.getTagIds()),
                post.getVersion()
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
            // 未软删除的文章，仅管理员可物理删除
            if (!UserContext.isAdmin()) {
                throw new IllegalStateException(PostErrorMessages.ONLY_ADMIN_PURGE_NOT_DELETED);
            }
            return;
        }
        
        // 已软删除的文章：所有者或管理员可物理删除
        if (!post.isOwnedBy(userId) && !UserContext.isAdmin()) {
            throw new PostOwnershipException(PostErrorMessages.NOT_OWNER_DELETE);
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
        postCacheInvalidationStore.evictAllRelated(postId);
        postCacheInvalidationStore.evictDetail(postId);
        postCacheInvalidationStore.evictLatestList();
        postCacheInvalidationStore.evictAuthorLists(post.getOwnerId());
        postCacheInvalidationStore.evictStats(postId);
        
        log.debug("Cleared all cache for post: {}", postId);
    }
}
