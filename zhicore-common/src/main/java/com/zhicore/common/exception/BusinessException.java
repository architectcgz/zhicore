package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;

/**
 * 业务异常
 * 用于业务逻辑校验失败等场景
 */
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
