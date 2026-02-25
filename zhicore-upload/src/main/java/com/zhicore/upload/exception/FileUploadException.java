package com.zhicore.upload.exception;

/**
 * 文件上传基础异常
 * 
 * 所有文件上传相关异常的父类
 */
public class FileUploadException extends RuntimeException {

    private final String errorCode;

    public FileUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileUploadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
