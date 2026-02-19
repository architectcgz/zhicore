package com.blog.upload.exception;

/**
 * 文件服务异常
 * 
 * 当调用 file-service 失败时抛出
 */
public class FileServiceException extends FileUploadException {

    public FileServiceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FileServiceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
