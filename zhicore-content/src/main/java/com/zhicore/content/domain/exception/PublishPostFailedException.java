package com.zhicore.content.domain.exception;

/**
 * 发布文章失败异常
 * 
 * @author ZhiCore Team
 */
public class PublishPostFailedException extends RuntimeException {
    
    public PublishPostFailedException(String message) {
        super(message);
    }
    
    public PublishPostFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
