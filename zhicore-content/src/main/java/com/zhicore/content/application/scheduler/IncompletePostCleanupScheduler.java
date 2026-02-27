package com.zhicore.content.application.scheduler;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.cache.LockKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 不完整文章清理调度器
 * 
 * 定期清理标记为 INCOMPLETE 的文章。
 * 每 10 分钟执行一次，确保数据最终一致性。
 * 
 * 特性：
 * 1. 使用分布式锁保护（多实例部署时防止重复执行）
 * 2. 清理操作是幂等的，重复执行不会产生副作用
 * 3. 使用固定 TTL（5分钟），清理操作执行时间可预测
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncompletePostCleanupScheduler {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;  // 不等待，获取不到直接返回
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(5);  // 锁持有时间5分钟
    
    private final PostRepository postRepository;
    private final PostContentStore contentStore;
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    /**
     * 清理不完整的文章
     * 
     * 每 10 分钟执行一次
     * 使用分布式锁确保多实例部署时只有一个实例执行清理任务
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void cleanupIncompletePosts() {
        String lockKey = lockKeys.incompletePostCleanup();
        
        // 尝试获取分布式锁（固定 TTL）
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
        
        if (!lockAcquired) {
            // 获取锁失败，说明其他实例正在执行，直接返回
            log.debug("无法获取不完整文章清理锁，跳过本次执行");
            return;
        }
        
        try {
            log.info("开始清理不完整文章");
            
            // 清理标记为 INCOMPLETE 的文章
            int cleanedPosts = cleanupIncompletePostsInternal();
            
            log.info("清理任务完成，清理文章数: {}", cleanedPosts);
            
        } catch (Exception e) {
            log.error("清理不完整文章失败", e);
        } finally {
            // 释放分布式锁
            lockManager.unlock(lockKey);
        }
    }
    
    /**
     * 清理标记为 INCOMPLETE 的文章
     * 
     * @return 清理的文章数量
     */
    private int cleanupIncompletePostsInternal() {
        List<Post> incompletePosts = postRepository.findByWriteState("INCOMPLETE");
        int cleanedCount = 0;
        
        for (Post post : incompletePosts) {
            try {
                // 删除 MongoDB 内容
                contentStore.deleteContent(post.getId());
                
                // 删除 PostgreSQL 记录
                postRepository.delete(post.getId());
                
                log.info("清理不完整文章成功: postId={}", post.getId());
                cleanedCount++;
                
            } catch (Exception e) {
                log.error("清理不完整文章失败: postId={}", post.getId(), e);
            }
        }
        
        return cleanedCount;
    }
}
