package com.zhicore.message.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 消息持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("messages")
public class MessagePO {

    /**
     * 消息ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 接收者ID
     */
    private Long receiverId;

    /**
     * 消息类型：0-文本 1-图片 2-文件
     */
    private Integer type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 媒体URL
     */
    private String mediaUrl;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 已读时间
     */
    private OffsetDateTime readAt;

    /**
     * 消息状态：0-已发送 1-已撤回
     */
    private Integer status;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
