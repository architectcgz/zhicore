package com.zhicore.upload.exception;

/**
 * 文件验证异常
 * 
 * 当文件验证失败时抛出，例如文件类型不支持、文件大小超限等
 */
public class FileValidationException extends FileUploadException {

    public FileValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FileValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
