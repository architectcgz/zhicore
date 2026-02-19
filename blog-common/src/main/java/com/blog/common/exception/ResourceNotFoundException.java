package com.blog.common.exception;

import com.blog.common.result.ResultCode;

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends BaseException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException() {
        super(ResultCode.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super(ResultCode.NOT_FOUND.getCode(), message);
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(ResultCode.NOT_FOUND.getCode(), 
              String.format("%s不存在: %s", resourceType, resourceId));
    }
}
