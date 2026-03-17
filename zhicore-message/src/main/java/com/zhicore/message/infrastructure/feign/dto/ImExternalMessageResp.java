package com.zhicore.message.infrastructure.feign.dto;

import lombok.Data;

@Data
public class ImExternalMessageResp {

    private String clientMsgId;
    private Long messageId;
    private String fromOpenId;
    private String toOpenId;
    private Long sequence;
    private Long timestamp;
}
