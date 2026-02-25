package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.CancelScheduleCommand;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.event.PostScheduleCancelledEvent;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 取消定时发布命令处理器
 * 
 * 验证文章状态并取消定时发布
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelScheduleHandler {
    
    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;
    
    /**
     * 处理取消定时发布命令
     * 
     * @param command 取消定时发布命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(CancelScheduleCommand command) {
        log.info("Cancelling scheduled publish: postId={}, userId={}", command.getPostId(), command.getUserId());
        
        // 加载文章
        Post post = postRepository.load(command.getPostId());
        
        // 验证权限
        if (!post.isOwnedBy(command.getUserId())) {
            throw new IllegalStateException("User does not own this post");
        }
        
        // 验证状态
        if (post.getStatus() != PostStatus.SCHEDULED) {
            throw new IllegalStateException("Post is not in SCHEDULED status: " + post.getStatus());
        }
        
        // 取消定时发布
        post.cancelSchedule();
        postRepository.update(post);
        log.info("Scheduled publish cancelled: postId={}", command.getPostId());
        
        // 发布事件
        PostScheduleCancelledEvent event = new PostScheduleCancelledEvent(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                Instant.now(),
                command.getPostId(),
                post.getVersion()
        );
        eventPublisher.publish(event);
        log.info("Published PostScheduleCancelled event: postId={}", command.getPostId());
    }
}
