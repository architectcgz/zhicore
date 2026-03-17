package com.zhicore.message.infrastructure.feign.dto;

import lombok.Data;

@Data
public class ImRecallMessageRequest {

    private Integer appId;
    private Long userId;
    private Long messageId;
}
