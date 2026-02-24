package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.SchedulePublishCommand;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostScheduledEvent;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 定时发布文章命令处理器
 * 
 * 设置文章的定时发布时间
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePublishHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    
    /**
     * 处理定时发布命令
     * 
     * @param command 定时发布命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(SchedulePublishCommand command) {
        log.info("Scheduling post publish: postId={}, userId={}, scheduledAt={}", 
                command.getPostId(), command.getUserId(), command.getScheduledAt());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new PostOwnershipException("无权设置定时发布：用户不是文章所有者");
        }
        
        // 验证时间
        if (command.getScheduledAt().isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }
        
        // 设置定时发布
        post.schedulePublish(command.getScheduledAt());
        postRepository.update(post);
        log.info("Post scheduled for publish: postId={}, scheduledAt={}", 
                command.getPostId(), command.getScheduledAt());
        
        // 发布事件（由调度器监听）
        PostScheduledEvent event = new PostScheduledEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                command.getPostId(),
                command.getScheduledAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostScheduled event: postId={}", command.getPostId());
    }
}
