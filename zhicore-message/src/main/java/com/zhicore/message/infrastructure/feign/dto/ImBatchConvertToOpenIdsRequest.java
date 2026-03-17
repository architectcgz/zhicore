package com.zhicore.message.infrastructure.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImBatchConvertToOpenIdsRequest {

    private Integer appId;
    private List<Long> userIds;
    private String provider;
}
