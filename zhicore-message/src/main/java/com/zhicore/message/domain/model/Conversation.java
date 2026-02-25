package com.zhicore.message.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 会话聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. participant1Id < participant2Id 保证会话唯一性
 *
 * @author ZhiCore Team
 */
@Getter
public class Conversation {

    /**
     * 会话ID（雪花ID）
     */
    private final Long id;

    /**
     * 参与者1 ID（较小的用户ID）
     */
    private final Long participant1Id;

    /**
     * 参与者2 ID（较大的用户ID）
     */
    private final Long participant2Id;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 最后一条消息ID
     */
    private Long lastMessageId;

    /**
     * 最后一条消息内容预览
     */
    private String lastMessageContent;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageAt;

    /**
     * 参与者1的未读消息数
     */
    private int unreadCount1;

    /**
     * 参与者2的未读消息数
     */
    private int unreadCount2;

    /**
     * 最后消息内容预览最大长度
     */
    private static final int MAX_PREVIEW_LENGTH = 100;

    /**
     * 私有构造函数
     */
    private Conversation(Long id, Long participant1Id, Long participant2Id) {
        Assert.notNull(id, "会话ID不能为空");
        Assert.isTrue(id > 0, "会话ID必须为正数");
        Assert.notNull(participant1Id, "参与者1 ID不能为空");
        Assert.isTrue(participant1Id > 0, "参与者1 ID必须为正数");
        Assert.notNull(participant2Id, "参与者2 ID不能为空");
        Assert.isTrue(participant2Id > 0, "参与者2 ID必须为正数");

        this.id = id;
        this.participant1Id = participant1Id;
        this.participant2Id = participant2Id;
        this.unreadCount1 = 0;
        this.unreadCount2 = 0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 私有构造函数（用于从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Conversation(@JsonProperty("id") Long id,
                         @JsonProperty("participant1Id") Long participant1Id,
                         @JsonProperty("participant2Id") Long participant2Id,
                         @JsonProperty("lastMessageId") Long lastMessageId,
                         @JsonProperty("lastMessageContent") String lastMessageContent,
                         @JsonProperty("lastMessageAt") LocalDateTime lastMessageAt,
                         @JsonProperty("unreadCount1") int unreadCount1,
                         @JsonProperty("unreadCount2") int unreadCount2,
                         @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.participant1Id = participant1Id;
        this.participant2Id = participant2Id;
        this.lastMessageId = lastMessageId;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount1 = unreadCount1;
        this.unreadCount2 = unreadCount2;
        this.createdAt = createdAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建会话
     * 确保 participant1Id < participant2Id，保证唯一性
     *
     * @param id 会话ID
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 会话实例
     */
    public static Conversation create(Long id, Long userId1, Long userId2) {
        Assert.notNull(userId1, "用户1 ID不能为空");
        Assert.isTrue(userId1 > 0, "用户1 ID必须为正数");
        Assert.notNull(userId2, "用户2 ID不能为空");
        Assert.isTrue(userId2 > 0, "用户2 ID必须为正数");
        
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("不能与自己创建会话");
        }

        // 确保 participant1Id < participant2Id，保证唯一性
        Long p1 = userId1 < userId2 ? userId1 : userId2;
        Long p2 = userId1 < userId2 ? userId2 : userId1;

        return new Conversation(id, p1, p2);
    }

    /**
     * 从持久化恢复会话
     *
     * @return 会话实例
     */
    public static Conversation reconstitute(Long id, Long participant1Id, Long participant2Id,
                                            Long lastMessageId, String lastMessageContent, 
                                            LocalDateTime lastMessageAt,
                                            int unreadCount1, int unreadCount2, 
                                            LocalDateTime createdAt) {
        return new Conversation(id, participant1Id, participant2Id, lastMessageId, 
                lastMessageContent, lastMessageAt, unreadCount1, unreadCount2, createdAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 更新最后消息
     *
     * @param message 消息
     */
    public void updateLastMessage(Message message) {
        Assert.notNull(message, "消息不能为空");
        
        this.lastMessageId = message.getId();
        this.lastMessageContent = message.getPreviewContent(MAX_PREVIEW_LENGTH);
        this.lastMessageAt = message.getCreatedAt();

        // 增加对方的未读数
        if (message.getSenderId().equals(participant1Id)) {
            this.unreadCount2++;
        } else {
            this.unreadCount1++;
        }
    }

    /**
     * 清除指定用户的未读数
     *
     * @param userId 用户ID
     */
    public void clearUnreadCount(Long userId) {
        if (userId.equals(participant1Id)) {
            this.unreadCount1 = 0;
        } else if (userId.equals(participant2Id)) {
            this.unreadCount2 = 0;
        }
    }

    /**
     * 获取对方参与者ID
     *
     * @param userId 当前用户ID
     * @return 对方用户ID
     */
    public Long getOtherParticipant(Long userId) {
        if (userId.equals(participant1Id)) {
            return participant2Id;
        } else if (userId.equals(participant2Id)) {
            return participant1Id;
        }
        throw new IllegalArgumentException("用户不是该会话的参与者");
    }

    /**
     * 获取指定用户的未读数
     *
     * @param userId 用户ID
     * @return 未读数
     */
    public int getUnreadCount(Long userId) {
        if (userId.equals(participant1Id)) {
            return unreadCount1;
        } else if (userId.equals(participant2Id)) {
            return unreadCount2;
        }
        return 0;
    }

    /**
     * 检查用户是否是会话参与者
     *
     * @param userId 用户ID
     * @return 是否是参与者
     */
    public boolean isParticipant(Long userId) {
        return userId.equals(participant1Id) || userId.equals(participant2Id);
    }

    /**
     * 获取规范化的参与者ID对（用于查询）
     * 确保 userId1 < userId2
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 规范化的参与者ID数组 [p1, p2]
     */
    public static Long[] normalizeParticipants(Long userId1, Long userId2) {
        Long p1 = userId1 < userId2 ? userId1 : userId2;
        Long p2 = userId1 < userId2 ? userId2 : userId1;
        return new Long[]{p1, p2};
    }
}
