package com.zhicore.content.infrastructure.messaging.consumer;

import com.zhicore.clients.client.UserServiceClient;
import com.zhicore.clients.dto.user.UserSimpleDTO;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.application.port.repo.PostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.integration.messaging.post.AuthorInfoCompensationIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 作者信息补偿消费者
 * 
 * 当创建文章时 user-service 不可用，使用默认值（owner_name="未知用户"）后，
 * 通过延迟消息重试获取作者信息并更新文章。
 * 
 * 补偿策略：
 * - 延迟级别：5（1 分钟）
 * - 只补偿一次，不重复重试
 * - 如果补偿失败，由定时扫描任务兜底
 * 
 * 消费配置：
 * - Topic: author-info-compensation
 * - Consumer Group: post-service-author-compensation-consumer
 * 
 * 处理流程：
 * 1. 解析 JSON 消息为 AuthorInfoCompensationEvent
 * 2. 查询文章，检查是否仍为默认值（"未知用户"）
 * 3. 如果已更新则跳过
 * 4. 重试获取作者信息
 * 5. 使用 updateAuthorInfo 方法更新文章
 * 6. 记录详细的日志
 * 
 * 幂等性保证：
 * 通过检查 owner_name 是否为 "未知用户" 来判断是否需要补偿，
 * 避免重复更新已经正常的文章。
 * 
 * @author ZhiCore Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = "author-info-compensation",
    consumerGroup = "post-service-author-compensation-consumer"
)
public class AuthorInfoCompensationConsumer implements RocketMQListener<String> {

    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    /**
     * 处理作者信息补偿消息
     * 
     * 当接收到补偿事件时，检查文章的作者信息是否仍为默认值，
     * 如果是则重试获取作者信息并更新。
     * 
     * 处理逻辑：
     * 1. 解析消息为 AuthorInfoCompensationEvent
     * 2. 查询文章，验证文章是否存在
     * 3. 检查 owner_name 是否为 "未知用户"
     * 4. 如果已更新则跳过（幂等性）
     * 5. 调用 user-service 获取作者信息
     * 6. 使用 updateAuthorInfo 方法批量更新（统一走版本过滤逻辑）
     * 7. 记录补偿结果
     * 
     * 异常处理：
     * - 文章不存在：记录警告日志，不抛出异常
     * - 已更新：记录信息日志，跳过处理
     * - 获取用户信息失败：记录警告日志，不重试（避免无限循环）
     * - 消息解析失败：记录错误日志，抛出异常触发重试
     * 
     * @param message JSON 格式的消息内容
     * @throws RuntimeException 当消息解析失败时抛出，触发重试
     */
    @Override
    public void onMessage(String message) {
        try {
            log.info("收到作者信息补偿消息: {}", message);
            
            // 解析消息
            AuthorInfoCompensationIntegrationEvent event = objectMapper.readValue(
                message, 
                AuthorInfoCompensationIntegrationEvent.class
            );
            
            log.info("开始处理作者信息补偿: postId={}, userId={}, occurredAt={}", 
                event.getPostId(), event.getUserId(), event.getOccurredAt());
            
            // 查询文章
            Post post = postRepository.findById(event.getPostId()).orElse(null);
            if (post == null) {
                log.warn("文章不存在，跳过补偿: postId={}", event.getPostId());
                return;
            }
            
            // 检查是否仍然是默认值
            if (!post.hasDefaultAuthorInfo()) {
                log.info("文章作者信息已更新，跳过补偿: postId={}, currentOwnerName={}", 
                    event.getPostId(), post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
                return;
            }
            
            // 重试获取作者信息
            try {
                log.debug("调用 user-service 获取作者信息: userId={}", event.getUserId());
                
                ApiResponse<List<UserSimpleDTO>> response = 
                    userServiceClient.getUsersSimple(Collections.singletonList(event.getUserId()));
                
                if (response != null && response.isSuccess() && 
                    response.getData() != null && !response.getData().isEmpty()) {
                    
                    UserSimpleDTO author = response.getData().get(0);
                    
                    log.info("成功获取作者信息: userId={}, nickname={}, avatarId={}, profileVersion={}", 
                        event.getUserId(), author.getNickname(), author.getAvatarId(),
                        author.getProfileVersion());
                    
                    // 使用 updateAuthorInfo 方法，统一走版本过滤逻辑
                    int updatedCount = postRepository.updateAuthorInfo(
                        event.getUserId(),
                        author.getNickname(),
                        author.getAvatarId(),
                        author.getProfileVersion() != null ? author.getProfileVersion() : 0L
                    );
                    
                    if (updatedCount > 0) {
                        log.info("作者信息补偿成功: postId={}, userId={}, nickname={}, updatedCount={}", 
                            event.getPostId(), event.getUserId(), author.getNickname(), updatedCount);
                    } else {
                        log.warn("作者信息补偿未更新任何文章（可能版本号过滤）: postId={}, userId={}", 
                            event.getPostId(), event.getUserId());
                    }
                    
                } else {
                    log.warn("补偿时获取用户信息失败: postId={}, userId={}, response={}", 
                        event.getPostId(), event.getUserId(), 
                        response != null ? response.getMessage() : "null");
                    // 补偿失败，不再重试（避免无限循环）
                    // 由定时扫描任务兜底
                }
                
            } catch (Exception e) {
                log.error("补偿作者信息时发生异常: postId={}, userId={}, error={}", 
                    event.getPostId(), event.getUserId(), e.getMessage(), e);
                // 补偿失败，不再重试
                // 由定时扫描任务兜底
            }
            
        } catch (Exception e) {
            log.error("处理作者信息补偿消息失败: message={}, error={}", message, e.getMessage(), e);
            // 抛出异常触发 RocketMQ 重试机制
            throw new RuntimeException("处理作者信息补偿消息失败: " + e.getMessage(), e);
        }
    }
}
