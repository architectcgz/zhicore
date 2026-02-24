package com.zhicore.common.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 错误补充信息
 *
 * 用于在不破坏既有 ApiResponse 结构的前提下，补充错误码与重试建议等字段。
 */
@Data
@AllArgsConstructor
public class ErrorInfo {

    /**
     * 业务错误码（字符串），用于精确区分场景（例如并发冲突/分页规则冲突等）
     */
    @JsonProperty("error_code")
    private String errorCode;

    /**
     * 是否建议客户端重试（例如乐观锁冲突）
     */
    @JsonProperty("retry_suggested")
    private boolean retrySuggested;
}

