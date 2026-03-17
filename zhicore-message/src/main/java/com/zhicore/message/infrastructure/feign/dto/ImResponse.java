package com.zhicore.message.infrastructure.feign.dto;

import lombok.Data;

@Data
public class ImResponse<T> {

    private int code;
    private String message;
    private T data;
    private String correlationId;

    public boolean isSuccess() {
        return code == 200;
    }
}
