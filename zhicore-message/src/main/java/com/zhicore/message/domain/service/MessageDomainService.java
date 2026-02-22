package com.zhicore.message.domain.service;

import com.zhicore.common.exception.DomainException;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 消息领域服务
 * 
 * 处理跨聚合的业务逻辑
 *
 * @author ZhiCore Team
 */
@Service
@RequiredArgsConstructor
public class MessageDomainService {

    private final ConversationRepository conversationRepository;

    /**
     * 获取或创建会话
     *
     * @param conversationId 新会话ID（如果需要创建）
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 会话
     */
    public Conversation getOrCreateConversation(Long conversationId, Long userId1, Long userId2) {
        // 规范化参与者ID
        Long[] participants = Conversation.normalizeParticipants(userId1, userId2);
        Long p1 = participants[0];
        Long p2 = participants[1];

        // 查找现有会话
        Optional<Conversation> existingConversation = conversationRepository.findByParticipants(p1, p2);
        
        if (existingConversation.isPresent()) {
            return existingConversation.get();
        }

        // 创建新会话
        Conversation conversation = Conversation.create(conversationId, userId1, userId2);
        conversationRepository.save(conversation);
        return conversation;
    }

    /**
     * 验证用户是否可以访问会话
     *
     * @param conversation 会话
     * @param userId 用户ID
     */
    public void validateConversationAccess(Conversation conversation, Long userId) {
        if (!conversation.isParticipant(userId)) {
            throw new DomainException("无权访问该会话");
        }
    }

    /**
     * 验证用户是否可以发送消息给目标用户
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param isBlocked 是否被拉黑
     * @param isStranger 是否是陌生人
     * @param strangerMessageAllowed 是否允许陌生人消息
     */
    public void validateMessagePermission(Long senderId, Long receiverId, 
                                          boolean isBlocked, boolean isStranger, 
                                          boolean strangerMessageAllowed) {
        if (senderId.equals(receiverId)) {
            throw new DomainException("不能给自己发送消息");
        }
        
        if (isBlocked) {
            throw new DomainException("您已被对方拉黑，无法发送消息");
        }
        
        if (isStranger && !strangerMessageAllowed) {
            throw new DomainException("对方不接收陌生人消息");
        }
    }

    /**
     * 更新会话的最后消息
     *
     * @param conversation 会话
     * @param message 消息
     */
    public void updateConversationLastMessage(Conversation conversation, Message message) {
        conversation.updateLastMessage(message);
        conversationRepository.update(conversation);
    }
}
