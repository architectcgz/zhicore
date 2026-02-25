package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;

/**
 * 未授权异常
 */
public class UnauthorizedException extends BaseException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException() {
        super(ResultCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(ResultCode.UNAUTHORIZED.getCode(), message);
    }
}
