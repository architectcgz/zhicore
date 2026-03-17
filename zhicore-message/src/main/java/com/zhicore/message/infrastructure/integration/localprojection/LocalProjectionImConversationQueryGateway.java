package com.zhicore.message.infrastructure.integration.localprojection;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImConversationView;
import com.zhicore.message.application.port.im.ImConversationQueryGateway;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * 默认查询适配器。
 *
 * 当远端 IM provider 尚未稳定时，先由本地 projection 实现查询契约；
 * 后续 provider adapter 就绪后，可以直接替换该实现而不改 application。
 */
@RequiredArgsConstructor
public class LocalProjectionImConversationQueryGateway implements ImConversationQueryGateway {

    private final ConversationRepository conversationRepository;

    @Override
    public List<ImConversationView> listConversations(Long userId, Long cursor, int limit) {
        return conversationRepository.findByUserId(userId, cursor, limit).stream()
                .map(conversation -> toView(conversation, userId))
                .toList();
    }

    @Override
    public Optional<ImConversationView> findConversation(Long userId, DirectConversationRef conversationRef) {
        return conversationRepository.findByParticipants(
                        conversationRef.participant1Id(),
                        conversationRef.participant2Id()
                )
                .filter(conversation -> conversation.isParticipant(userId))
                .map(conversation -> toView(conversation, userId));
    }

    @Override
    public int countConversations(Long userId) {
        return conversationRepository.countByUserId(userId);
    }

    private ImConversationView toView(Conversation conversation, Long userId) {
        return ImConversationView.builder()
                .localConversationId(conversation.getId())
                .conversationRef(DirectConversationRef.of(
                        conversation.getParticipant1Id(),
                        conversation.getParticipant2Id()
                ))
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount(userId))
                .createdAt(conversation.getCreatedAt())
                .build();
    }
}
