package com.zhicore.message.infrastructure.integration.localprojection;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImMessageView;
import com.zhicore.message.application.port.im.ImMessageQueryGateway;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 默认消息查询适配器。
 *
 * 基于本地 projection 对外提供统一消息查询能力，
 * 后续远端 provider 可实现相同端口完成无侵入切换。
 */
@RequiredArgsConstructor
public class LocalProjectionImMessageQueryGateway implements ImMessageQueryGateway {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Override
    public List<ImMessageView> getMessageHistory(Long userId, DirectConversationRef conversationRef, Long cursor, int limit) {
        return conversationRepository.findByParticipants(
                        conversationRef.participant1Id(),
                        conversationRef.participant2Id()
                )
                .filter(conversation -> conversation.isParticipant(userId))
                .map(conversation -> messageRepository.findByConversationId(conversation.getId(), cursor, limit).stream()
                        .map(message -> toView(message, conversation.getId()))
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public int countUnreadMessages(Long userId) {
        return messageRepository.countUnreadByUserId(userId);
    }

    private ImMessageView toView(Message message, Long localConversationId) {
        return ImMessageView.builder()
                .localMessageId(message.getId())
                .localConversationId(localConversationId)
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .type(message.getType())
                .content(message.getContent())
                .mediaUrl(message.getMediaUrl())
                .read(message.isRead())
                .readAt(message.getReadAt())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
