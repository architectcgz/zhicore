package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;

/**
 * 请求频率限制异常（HTTP 429）
 *
 * 用于需要明确返回 429 的场景（例如管理端手动重试接口的限频）。
 */
public class TooManyRequestsException extends BaseException {

    private static final long serialVersionUID = 1L;

    public TooManyRequestsException(String message) {
        super(ResultCode.TOO_MANY_REQUESTS, message);
    }
}

