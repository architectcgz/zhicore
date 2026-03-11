package com.zhicore.message.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.sentinel.MessageSentinelHandlers;
import com.zhicore.message.application.sentinel.MessageSentinelResources;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息读服务。
 *
 * 负责消息历史与未读数查询，不承载写操作。
 */
@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageDomainService messageDomainService;

    @SentinelResource(
            value = MessageSentinelResources.GET_MESSAGE_HISTORY,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleMessageHistoryBlocked"
    )
    public List<MessageVO> getMessageHistory(Long conversationId, Long cursor, int limit) {
        Long userId = UserContext.getUserId();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));
        messageDomainService.validateConversationAccess(conversation, userId);

        return messageRepository.findByConversationId(conversationId, cursor, limit).stream()
                .map(message -> toMessageVO(message, userId))
                .collect(Collectors.toList());
    }

    @SentinelResource(
            value = MessageSentinelResources.GET_UNREAD_COUNT,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleUnreadCountBlocked"
    )
    public int getUnreadCount() {
        Long userId = UserContext.getUserId();
        return messageRepository.countUnreadByUserId(userId);
    }

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
}
