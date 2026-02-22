package com.zhicore.common.exception;

/**
 * 文件不存在异常
 * 
 * 当请求的文件不存在时抛出此异常
 */
public class FileNotFoundException extends RuntimeException {
    
    public FileNotFoundException(String fileId) {
        super("文件不存在: " + fileId);
    }
    
    public FileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
