package com.blog.post.infrastructure.mq;

import com.blog.api.event.user.UserProfileUpdatedEvent;
import com.blog.post.application.service.PostAuthorSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 用户资料变更事件消费者
 * 
 * <p>消费 user-profile-changed Topic 的消息，当用户资料发生变更时，
 * 同步更新该用户所有文章的作者信息冗余字段。
 * 
 * <p>消费配置：
 * <ul>
 *   <li>Topic: user-profile-changed</li>
 *   <li>Consumer Group: post-service-user-profile-consumer</li>
 *   <li>Tag: profile-updated</li>
 * </ul>
 * 
 * <p>消息格式：{@link UserProfileUpdatedEvent}
 * 
 * <p>处理流程：
 * <ol>
 *   <li>解析 JSON 消息为 UserProfileUpdatedEvent</li>
 *   <li>调用 PostAuthorSyncService 同步作者信息</li>
 *   <li>记录处理结果日志</li>
 *   <li>失败时抛出异常触发重试</li>
 * </ol>
 * 
 * @author blog-post-service
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "user-profile-changed",
    consumerGroup = "post-service-user-profile-consumer",
    selectorExpression = "profile-updated"
)
public class UserProfileChangedConsumer implements RocketMQListener<String> {

    private final PostAuthorSyncService postAuthorSyncService;
    private final ObjectMapper objectMapper;

    /**
     * 处理用户资料变更消息
     * 
     * <p>当接收到用户资料变更事件时，解析消息并同步更新该用户所有文章的作者信息。
     * 如果处理失败，会抛出异常触发 RocketMQ 的重试机制。
     * 
     * @param message JSON 格式的消息内容
     * @throws RuntimeException 当消息解析或处理失败时抛出，触发重试
     */
    @Override
    public void onMessage(String message) {
        try {
            log.info("收到用户资料变更消息: {}", message);
            
            // 解析消息
            UserProfileUpdatedEvent event = objectMapper.readValue(message, UserProfileUpdatedEvent.class);
            
            log.info("开始同步用户 {} 的作者信息，昵称: {}, 头像: {}", 
                    event.getUserId(), event.getNickname(), event.getAvatar());
            
            // 同步作者信息
            postAuthorSyncService.syncAuthorInfo(event);
            
            log.info("成功同步用户 {} 的作者信息", event.getUserId());
            
        } catch (Exception e) {
            log.error("处理用户资料变更消息失败: {}, 错误: {}", message, e.getMessage(), e);
            // 抛出异常触发 RocketMQ 重试机制
            throw new RuntimeException("处理用户资料变更消息失败: " + e.getMessage(), e);
        }
    }
}
