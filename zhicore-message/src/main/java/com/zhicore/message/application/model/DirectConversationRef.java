package com.zhicore.message.application.model;

import org.springframework.util.Assert;

/**
 * 单聊会话的稳定业务标识。
 *
 * 不暴露底层 provider 的 conversationId 生成规则，
 * application 统一以参与者关系表达单聊会话。
 */
public record DirectConversationRef(Long participant1Id, Long participant2Id) {

    public DirectConversationRef {
        Assert.notNull(participant1Id, "participant1Id 不能为空");
        Assert.notNull(participant2Id, "participant2Id 不能为空");
        Assert.isTrue(participant1Id > 0, "participant1Id 必须为正数");
        Assert.isTrue(participant2Id > 0, "participant2Id 必须为正数");
        Assert.isTrue(!participant1Id.equals(participant2Id), "单聊参与者不能相同");
        Assert.isTrue(participant1Id < participant2Id, "participant1Id 必须小于 participant2Id");
    }

    public static DirectConversationRef of(Long userId1, Long userId2) {
        Assert.notNull(userId1, "userId1 不能为空");
        Assert.notNull(userId2, "userId2 不能为空");
        return userId1 < userId2
                ? new DirectConversationRef(userId1, userId2)
                : new DirectConversationRef(userId2, userId1);
    }

    public boolean contains(Long userId) {
        return participant1Id.equals(userId) || participant2Id.equals(userId);
    }

    public Long otherParticipant(Long userId) {
        if (participant1Id.equals(userId)) {
            return participant2Id;
        }
        if (participant2Id.equals(userId)) {
            return participant1Id;
        }
        throw new IllegalArgumentException("用户不是该会话参与者");
    }
}
