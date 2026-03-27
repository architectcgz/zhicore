package com.zhicore.message.infrastructure.integration.localprojection;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImMessageView;
import com.zhicore.message.application.port.im.ImMessageQueryGateway;
import com.zhicore.message.domain.repository.ConversationRepository;
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

    @Override
    public List<ImMessageView> getMessageHistory(Long userId, DirectConversationRef conversationRef, Long cursor, int limit) {
        return List.of();
    }

    @Override
    public int countUnreadMessages(Long userId) {
        return conversationRepository.countUnreadByUserId(userId);
    }
}
