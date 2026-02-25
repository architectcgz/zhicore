package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数校验异常
 * 用于参数校验失败场景，支持多字段错误信息
 */
@Getter
public class ValidationException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 字段错误列表
     */
    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        super(ResultCode.PARAM_ERROR.getCode(), message);
        this.fieldErrors = new ArrayList<>();
    }

    public ValidationException(List<FieldError> fieldErrors) {
        super(ResultCode.PARAM_ERROR.getCode(), "参数校验失败");
        this.fieldErrors = fieldErrors != null ? fieldErrors : new ArrayList<>();
    }

    public ValidationException(String field, String message) {
        super(ResultCode.PARAM_ERROR.getCode(), message);
        this.fieldErrors = new ArrayList<>();
        this.fieldErrors.add(new FieldError(field, message));
    }

    /**
     * 字段错误信息
     */
    @Getter
    public static class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}
