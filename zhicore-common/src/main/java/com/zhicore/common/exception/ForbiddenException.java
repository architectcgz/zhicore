package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;

/**
 * 禁止访问异常
 */
public class ForbiddenException extends BaseException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException() {
        super(ResultCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ResultCode.FORBIDDEN.getCode(), message);
    }
}
