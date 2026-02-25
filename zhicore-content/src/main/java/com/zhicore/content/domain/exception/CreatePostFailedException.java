package com.zhicore.content.domain.exception;

/**
 * 创建文章失败异常
 * 用于文章创建工作流（CreatePostWorkflow 和 CreateDraftWorkflow）执行失败时抛出
 * 
 * @author ZhiCore Team
 */
public class CreatePostFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public CreatePostFailedException(String message) {
        super(message);
    }
    
    public CreatePostFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
