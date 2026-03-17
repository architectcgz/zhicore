package com.zhicore.message.domain.service;

import com.zhicore.common.exception.DomainException;
import com.zhicore.message.domain.model.Conversation;
import org.springframework.stereotype.Service;

/**
 * 消息领域服务
 * 
 * 处理跨聚合的业务逻辑
 *
 * @author ZhiCore Team
 */
@Service
public class MessageDomainService {

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

}
