package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;

/**
 * 服务不可用异常（HTTP 503）
 *
 * 用于依赖服务（例如 ID 服务）不可用且不允许本地降级的场景。
 */
public class ServiceUnavailableException extends BaseException {

    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(ResultCode.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(ResultCode.SERVICE_UNAVAILABLE.getCode(), message, cause);
    }
}

