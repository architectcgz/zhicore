package com.zhicore.content.application.scheduler;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.content.application.port.cachekey.SchedulerLockKeyResolver;
import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 已消费事件清理调度器
 * 
 * 定期清理过期的消费记录，避免 consumed_events 表无限增长。
 * 每天凌晨 2 点执行，清理 30 天前的记录。
 * 
 * 特性：
 * 1. 使用分布式锁保护（多实例部署时防止重复执行）
 * 2. 清理操作是幂等的，重复执行影响较小
 * 3. 使用固定 TTL（5分钟），清理操作执行时间可预测
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumedEventCleanupScheduler {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;  // 不等待，获取不到直接返回
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(5);  // 锁持有时间5分钟
    
    private final ConsumedEventRepository consumedEventRepository;
    private final LockManager lockManager;
    private final SchedulerLockKeyResolver schedulerLockKeyResolver;
    
    /**
     * 清理过期的消费记录
     * 
     * 每天凌晨 2 点执行，清理 30 天前的记录
     * 使用分布式锁确保多实例部署时只有一个实例执行清理任务
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldEvents() {
        String lockKey = schedulerLockKeyResolver.consumedEventCleanup();
        
        // 尝试获取分布式锁（固定 TTL）
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME);
        
        if (!lockAcquired) {
            // 获取锁失败，说明其他实例正在执行，直接返回
            log.debug("无法获取消费事件清理锁，跳过本次执行");
            return;
        }
        
        try {
            // 清理 30 天前的记录
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            
            log.info("开始清理过期的消费记录，截止时间: {}", cutoff);
            
            int deletedCount = consumedEventRepository.cleanupBefore(cutoff);
            
            log.info("清理过期消费记录完成，删除记录数: {}", deletedCount);
            
        } catch (Exception e) {
            log.error("清理过期消费记录失败", e);
        } finally {
            // 释放分布式锁
            lockManager.unlock(lockKey);
        }
    }
}
