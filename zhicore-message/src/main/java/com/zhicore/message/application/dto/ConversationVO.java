package com.zhicore.message.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 会话视图对象
 *
 * @author ZhiCore Team
 */
@Data
@Builder
public class ConversationVO {

    /**
     * 会话ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 对方用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long otherUserId;

    /**
     * 对方用户昵称
     */
    private String otherUserNickName;

    /**
     * 对方用户头像
     */
    private String otherUserAvatarUrl;

    /**
     * 最后一条消息内容预览
     */
    private String lastMessageContent;

    /**
     * 最后一条消息时间
     */
    private OffsetDateTime lastMessageAt;

    /**
     * 未读消息数
     */
    private int unreadCount;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
