package com.zhicore.content.infrastructure.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 分布式锁键常量
 * 
 * 统一管理所有分布式锁的键，确保命名规范和跨服务隔离。
 * 
 * 命名格式：{env}:{service}:{category}:{resource}:{action}:lock
 * 
 * 示例：
 * - dev:zhicore-content:outbox:dispatcher:dispatch:lock
 * - prod:zhicore-content:scheduler:consumed-event-cleanup:cleanup:lock
 * 
 * @author ZhiCore Team
 */
@Component
public class LockKeys {
    
    /**
     * 环境标识
     * 
     * 注意：不直接使用 spring.profiles.active，因为它可能包含多个 profile（如 "prod,k8s"）
     * 推荐配置：在 application.yml 中添加 app.env=dev|test|prod
     * 
     * 如果未配置 app.env，则从 spring.profiles.active 中提取第一个 profile
     */
    @Value("${app.env:#{null}}")
    private String appEnv;
    
    @Value("${spring.profiles.active:dev}")
    private String profilesActive;
    
    @Value("${spring.application.name:zhicore-content}")
    private String serviceName;
    
    /**
     * 获取环境标识
     * 
     * 优先使用 app.env，如果未配置则从 spring.profiles.active 中提取第一个 profile
     */
    private String getEnv() {
        if (appEnv != null && !appEnv.isEmpty()) {
            return appEnv;
        }
        
        // 从 spring.profiles.active 中提取第一个 profile
        if (profilesActive != null && !profilesActive.isEmpty()) {
            String[] profiles = profilesActive.split(",");
            return profiles[0].trim();
        }
        
        return "dev";
    }
    
    /**
     * Outbox 事件投递锁
     */
    public String outboxDispatcher() {
        return buildKey("outbox", "dispatcher", "dispatch");
    }
    
    /**
     * 域内投影任务派发锁
     */
    public String internalEventDispatcher() {
        return buildKey("internal-event", "dispatcher", "dispatch");
    }
    
    /**
     * 消费事件清理锁
     */
    public String consumedEventCleanup() {
        return buildKey("scheduler", "consumed-event-cleanup", "cleanup");
    }
    
    /**
     * 不完整文章清理锁
     */
    public String incompletePostCleanup() {
        return buildKey("scheduler", "incomplete-post-cleanup", "cleanup");
    }
    
    /**
     * 作者信息回填锁
     */
    public String authorInfoBackfill() {
        return buildKey("scheduler", "author-info-backfill", "backfill");
    }
    
    /**
     * 构建锁键
     * 
     * @param category 类别（如 outbox, scheduler）
     * @param resource 资源（如 dispatcher, consumed-event-cleanup）
     * @param action 操作（如 dispatch, cleanup）
     * @return 完整的锁键
     */
    private String buildKey(String category, String resource, String action) {
        return String.format("%s:%s:%s:%s:%s:lock", 
            getEnv(), serviceName, category, resource, action);
    }
}
