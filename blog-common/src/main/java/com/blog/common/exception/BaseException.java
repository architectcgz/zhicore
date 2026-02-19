package com.blog.common.exception;

import com.blog.common.result.ResultCode;
import lombok.Getter;

/**
 * 基础异常类
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final int code;

    public BaseException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BaseException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.FAIL.getCode();
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
