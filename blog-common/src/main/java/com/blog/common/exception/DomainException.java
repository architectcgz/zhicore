package com.blog.common.exception;

import com.blog.common.result.ResultCode;

/**
 * 领域异常
 * 用于领域层业务规则校验失败
 */
public class DomainException extends BaseException {

    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }

    public DomainException(int code, String message) {
        super(code, message);
    }

    public DomainException(ResultCode resultCode) {
        super(resultCode);
    }

    public DomainException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
