package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.UpdatePostTagsCommand;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 更新文章标签命令处理器
 * 
 * 验证权限并更新文章标签
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePostTagsHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final CacheRepository cacheRepository;
    
    /**
     * 处理更新标签命令
     * 
     * @param command 更新命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(UpdatePostTagsCommand command) {
        log.info("Updating post tags: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException("无权更新此文章：用户不是文章所有者");
        }
        
        // 保存旧标签用于事件和缓存失效
        Set<TagId> oldTagIds = new HashSet<>(post.getTagIds());
        
        // 更新标签
        post.updateTags(command.getNewTagIds());
        postRepository.update(post);
        log.info("Post tags updated: postId={}, oldTags={}, newTags={}", 
                command.getPostId(), oldTagIds.size(), command.getNewTagIds().size());
        
        // 失效缓存
        invalidateCache(command.getPostId(), oldTagIds, command.getNewTagIds());
        
        // 发布事件
        PostTagsUpdatedDomainEvent event = new PostTagsUpdatedDomainEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                command.getPostId(),
                oldTagIds,
                command.getNewTagIds(),
                Instant.now(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostTagsUpdated event: postId={}", command.getPostId());
    }
    
    /**
     * 失效相关缓存
     * 
     * 包括文章详情缓存和所有相关标签的列表缓存
     * 
     * 使用领域类型 PostId 和 TagId 作为参数，保持领域模型的完整性。
     * 在应用层方法中，我们已经有了这些领域对象，直接传递可以避免不必要的类型转换。
     * 
     * @param postId 文章 ID（领域类型）
     * @param oldTags 旧标签集合
     * @param newTags 新标签集合
     */
    private void invalidateCache(PostId postId, Set<TagId> oldTags, Set<TagId> newTags) {
        List<String> keys = new ArrayList<>();
        keys.add(PostRedisKeys.detail(postId));
        
        // 失效旧标签的列表缓存
        oldTags.forEach(tag -> keys.add(PostRedisKeys.listTag(tag)));
        
        // 失效新标签的列表缓存
        newTags.forEach(tag -> keys.add(PostRedisKeys.listTag(tag)));
        
        cacheRepository.delete(keys.toArray(new String[0]));
        log.debug("Invalidated cache for post and tags: postId={}, totalKeys={}", postId, keys.size());
    }
}
