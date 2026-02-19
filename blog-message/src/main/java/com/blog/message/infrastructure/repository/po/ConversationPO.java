package com.blog.message.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 会话持久化对象
 *
 * @author Blog Team
 */
@Data
@TableName("conversations")
public class ConversationPO {

    /**
     * 会话ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 参与者1 ID（较小的用户ID）
     */
    private Long participant1Id;

    /**
     * 参与者2 ID（较大的用户ID）
     */
    private Long participant2Id;

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
    private OffsetDateTime lastMessageAt;

    /**
     * 参与者1的未读消息数
     */
    private Integer unreadCount1;

    /**
     * 参与者2的未读消息数
     */
    private Integer unreadCount2;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
