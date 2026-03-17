package com.zhicore.message.application.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地消息与 IM 消息的桥接映射。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImMessageMapping {

    private Long imMessageId;
    private String imConversationId;
}
