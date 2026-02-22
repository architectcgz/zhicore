package com.zhicore.common.exception;

/**
 * 文件访问被拒绝异常
 * 
 * 当用户无权访问请求的文件时抛出此异常
 */
public class FileAccessDeniedException extends RuntimeException {
    
    public FileAccessDeniedException(String message) {
        super(message);
    }
    
    public FileAccessDeniedException(String fileId, String reason) {
        super(String.format("无权访问文件 %s: %s", fileId, reason));
    }
    
    public FileAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
