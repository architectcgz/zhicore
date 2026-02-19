package com.blog.api.event.post;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 作者信息补偿事件
 * 
 * 当创建文章时 user-service 不可用，使用默认值后
 * 通过延迟消息重试获取作者信息并更新
 * 
 * 使用场景：
 * 1. 创建文章时 user-service 不可用，使用默认值（owner_name="未知用户"）
 * 2. 发送延迟消息（延迟 1 分钟）
 * 3. 补偿消费者重试获取作者信息并更新文章
 * 
 * 补偿策略：
 * - 延迟级别：5（1 分钟）
 * - 只补偿一次，不重复重试
 * - 如果补偿失败，由定时扫描任务兜底
 */
@Getter
public class AuthorInfoCompensationEvent extends DomainEvent {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 文章ID
     */
    private final Long postId;
    
    /**
     * 用户ID（作者ID）
     */
    private final Long userId;
    
    /**
     * 事件创建时间
     */
    private final LocalDateTime createdAt;
    
    public AuthorInfoCompensationEvent(Long postId, Long userId, LocalDateTime createdAt) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
    
    @Override
    public String getTag() {
        return "AuthorInfoCompensation";
    }
}
