package com.zhicore.message.application.service.command;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.DomainException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.port.event.MessageTransactionEventPort;
import com.zhicore.message.application.port.id.MessageIdGenerator;
import com.zhicore.message.application.service.MessageRestrictionService;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息写服务。
 *
 * 负责发送、撤回、已读变更，不承载查询职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageCommandService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageDomainService messageDomainService;
    private final MessageRestrictionService messageRestrictionService;
    private final MessageIdGenerator messageIdGenerator;
    private final MessageTransactionEventPort messageTransactionEventPort;

    /**
     * 发送文本消息
     *
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @return 消息视图对象
     */
    @Transactional
    public MessageVO sendTextMessage(Long receiverId, String content) {
        return sendMessageInternal(receiverId, MessageType.TEXT, content, null);
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
        return sendMessageInternal(receiverId, MessageType.IMAGE, null, imageUrl);
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
        return sendMessageInternal(receiverId, MessageType.FILE, fileName, fileUrl);
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
        messageTransactionEventPort.publishMessageRecalled(
                MessageRecallSyncRequest.from(message));
        
        log.info("Message recalled: messageId={}, userId={}", messageId, userId);
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
     * 验证消息权限
     */
    private void validateMessagePermission(Long senderId, Long receiverId) {
        // 使用消息限制服务检查权限
        messageRestrictionService.checkCanSendMessage(senderId, receiverId);
    }

    private MessageVO sendMessageInternal(Long receiverId, MessageType type, String content, String mediaUrl) {
        Long senderId = UserContext.getUserId();
        validateMessagePermission(senderId, receiverId);

        Conversation conversation = getOrCreateConversation(senderId, receiverId);
        Long messageId = messageIdGenerator.nextId();
        Message message = createMessage(messageId, conversation.getId(), senderId, receiverId, type, content, mediaUrl);

        messageRepository.save(message);
        updateConversationLastMessage(conversation, message);
        publishAfterCommit(message);

        log.info("{} message sent: messageId={}, conversationId={}, senderId={}, receiverId={}",
                type, messageId, conversation.getId(), senderId, receiverId);
        return toMessageVO(message, senderId);
    }

    private Conversation getOrCreateConversation(Long senderId, Long receiverId) {
        Long[] participants = Conversation.normalizeParticipants(senderId, receiverId);
        return conversationRepository.findByParticipants(participants[0], participants[1])
                .orElseGet(() -> {
                    Long conversationId = messageIdGenerator.nextId();
                    Conversation conversation = Conversation.create(conversationId, senderId, receiverId);
                    conversationRepository.save(conversation);
                    return conversation;
                });
    }

    private void updateConversationLastMessage(Conversation conversation, Message message) {
        conversation.updateLastMessage(message);
        conversationRepository.update(conversation);
    }

    private Message createMessage(Long messageId, Long conversationId, Long senderId, Long receiverId,
                                  MessageType type, String content, String mediaUrl) {
        return switch (type) {
            case TEXT -> Message.createText(messageId, conversationId, senderId, receiverId, content);
            case IMAGE -> Message.createImage(messageId, conversationId, senderId, receiverId, mediaUrl);
            case FILE -> Message.createFile(messageId, conversationId, senderId, receiverId, content, mediaUrl);
        };
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
     * 在事务内登记发送后的 outbox 副作用，避免事务内直接触发外部系统。
     */
    private void publishAfterCommit(Message message) {
        messageTransactionEventPort.publishMessageSent(MessageSentPublishRequest.from(message));
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
