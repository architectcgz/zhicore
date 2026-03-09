package com.zhicore.message.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.DomainException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageApplicationService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageDomainService messageDomainService;
    private final MessageRestrictionService messageRestrictionService;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final UserServiceClient userServiceClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 发送文本消息
     *
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @return 消息视图对象
     */
    @Transactional
    public MessageVO sendTextMessage(Long receiverId, String content) {
        Long senderId = UserContext.getUserId();
        
        // 验证消息权限
        validateMessagePermission(senderId, receiverId);
        
        // 获取或创建会话
        Long conversationId = generateId();
        Conversation conversation = messageDomainService.getOrCreateConversation(
                conversationId, senderId, receiverId);
        
        // 创建消息
        Long messageId = generateId();
        Message message = Message.createText(messageId, conversation.getId(), 
                senderId, receiverId, content);
        
        // 保存消息
        messageRepository.save(message);
        
        // 更新会话最后消息
        messageDomainService.updateConversationLastMessage(conversation, message);
        
        // 事务提交后再触发 MQ 推送，避免事务回滚时产生幽灵事件
        publishAfterCommit(message);
        
        log.info("Text message sent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                messageId, conversation.getId(), senderId, receiverId);
        
        return toMessageVO(message, senderId);
    }

    /**
     * 发送图片消息
     *
     * @param receiverId 接收者ID
     * @param imageUrl 图片URL
     * @return 消息视图对象
     */
    @Transactional
    public MessageVO sendImageMessage(Long receiverId, String imageUrl) {
        Long senderId = UserContext.getUserId();
        
        // 验证消息权限
        validateMessagePermission(senderId, receiverId);
        
        // 获取或创建会话
        Long conversationId = generateId();
        Conversation conversation = messageDomainService.getOrCreateConversation(
                conversationId, senderId, receiverId);
        
        // 创建消息
        Long messageId = generateId();
        Message message = Message.createImage(messageId, conversation.getId(), 
                senderId, receiverId, imageUrl);
        
        // 保存消息
        messageRepository.save(message);
        
        // 更新会话最后消息
        messageDomainService.updateConversationLastMessage(conversation, message);
        
        // 事务提交后再触发 MQ 推送，避免事务回滚时产生幽灵事件
        publishAfterCommit(message);
        
        log.info("Image message sent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                messageId, conversation.getId(), senderId, receiverId);
        
        return toMessageVO(message, senderId);
    }

    /**
     * 发送文件消息
     *
     * @param receiverId 接收者ID
     * @param fileName 文件名
     * @param fileUrl 文件URL
     * @return 消息视图对象
     */
    @Transactional
    public MessageVO sendFileMessage(Long receiverId, String fileName, String fileUrl) {
        Long senderId = UserContext.getUserId();
        
        // 验证消息权限
        validateMessagePermission(senderId, receiverId);
        
        // 获取或创建会话
        Long conversationId = generateId();
        Conversation conversation = messageDomainService.getOrCreateConversation(
                conversationId, senderId, receiverId);
        
        // 创建消息
        Long messageId = generateId();
        Message message = Message.createFile(messageId, conversation.getId(), 
                senderId, receiverId, fileName, fileUrl);
        
        // 保存消息
        messageRepository.save(message);
        
        // 更新会话最后消息
        messageDomainService.updateConversationLastMessage(conversation, message);
        
        // 事务提交后再触发 MQ 推送，避免事务回滚时产生幽灵事件
        publishAfterCommit(message);
        
        log.info("File message sent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                messageId, conversation.getId(), senderId, receiverId);
        
        return toMessageVO(message, senderId);
    }

    /**
     * 发送消息（通用方法）
     *
     * @param receiverId 接收者ID
     * @param type 消息类型
     * @param content 消息内容
     * @param mediaUrl 媒体URL
     * @return 消息视图对象
     */
    @Transactional
    public MessageVO sendMessage(Long receiverId, MessageType type, String content, String mediaUrl) {
        return switch (type) {
            case TEXT -> sendTextMessage(receiverId, content);
            case IMAGE -> sendImageMessage(receiverId, mediaUrl);
            case FILE -> sendFileMessage(receiverId, content, mediaUrl);
        };
    }

    /**
     * 撤回消息
     *
     * @param messageId 消息ID
     */
    @Transactional
    public void recallMessage(Long messageId) {
        Long userId = UserContext.getUserId();
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ResultCode.MESSAGE_NOT_FOUND));
        
        try {
            message.recall(userId);
        } catch (DomainException e) {
            throw mapRecallException(e);
        }
        
        // 更新消息
        messageRepository.update(message);
        
        log.info("Message recalled: messageId={}, userId={}", messageId, userId);
    }

    /**
     * 查询消息历史（游标分页）
     *
     * @param conversationId 会话ID
     * @param cursor 游标
     * @param limit 数量限制
     * @return 消息列表
     */
    public List<MessageVO> getMessageHistory(Long conversationId, Long cursor, int limit) {
        Long userId = UserContext.getUserId();
        
        // 验证会话访问权限
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));
        messageDomainService.validateConversationAccess(conversation, userId);
        
        // 查询消息
        List<Message> messages = messageRepository.findByConversationId(conversationId, cursor, limit);
        
        return messages.stream()
                .map(m -> toMessageVO(m, userId))
                .collect(Collectors.toList());
    }

    /**
     * 标记消息为已读
     *
     * @param conversationId 会话ID
     */
    @Transactional
    public void markAsRead(Long conversationId) {
        Long userId = UserContext.getUserId();
        
        // 验证会话访问权限
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));
        messageDomainService.validateConversationAccess(conversation, userId);
        
        // 批量标记消息为已读
        messageRepository.markAsRead(conversationId, userId);
        
        // 清除会话未读数
        conversation.clearUnreadCount(userId);
        conversationRepository.update(conversation);
        
        log.info("Messages marked as read: conversationId={}, userId={}", conversationId, userId);
    }

    /**
     * 获取用户未读消息总数
     *
     * @return 未读消息数
     */
    public int getUnreadCount() {
        Long userId = UserContext.getUserId();
        return messageRepository.countUnreadByUserId(userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 生成分布式ID
     */
    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            log.error("生成ID失败: {}", response.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "ID生成失败");
        }
        return response.getData();
    }

    /**
     * 验证消息权限
     */
    private void validateMessagePermission(Long senderId, Long receiverId) {
        // 使用消息限制服务检查权限
        messageRestrictionService.checkCanSendMessage(senderId, receiverId);
    }

    /**
     * 转换为消息视图对象
     */
    private MessageVO toMessageVO(Message message, Long currentUserId) {
        return MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .type(message.getType())
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .isRead(message.isRead())
                .readAt(message.getReadAt())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .isSelf(message.getSenderId().equals(currentUserId))
                .build();
    }

    /**
     * 在 Spring 事务提交后发布消息发送事件，避免事务内直接发 MQ。
     */
    private void publishAfterCommit(Message message) {
        applicationEventPublisher.publishEvent(MessageSentPublishRequest.from(message));
    }

    private BusinessException mapRecallException(DomainException e) {
        String message = e.getMessage();
        if ("消息已经撤回".equals(message)) {
            return new BusinessException(ResultCode.MESSAGE_ALREADY_RECALLED, message);
        }
        if ("只能撤回自己发送的消息".equals(message)) {
            return new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, message);
        }
        if (message != null && message.contains("无法撤回")) {
            return new BusinessException(ResultCode.MESSAGE_RECALL_TIMEOUT, message);
        }
        return new BusinessException(ResultCode.OPERATION_FAILED, message);
    }
}
