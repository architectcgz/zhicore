package com.zhicore.message.infrastructure.feign.dto;

import lombok.Data;

@Data
public class ImSendMessageByOpenIdRequest {

    private Integer appId;
    private String fromOpenId;
    private String provider;
    private String toOpenId;
    private Integer conversationType;
    private Integer messageType;
    private String content;
    private String extra;
}
