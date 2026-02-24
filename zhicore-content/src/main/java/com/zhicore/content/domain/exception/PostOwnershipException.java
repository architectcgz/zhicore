package com.zhicore.content.domain.exception;

/**
 * 文章所有权异常
 * 
 * 当用户尝试操作不属于自己的文章时抛出此异常
 * 
 * @author ZhiCore Team
 */
public class PostOwnershipException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PostOwnershipException(String message) {
        super(message);
    }

    public PostOwnershipException(String message, Throwable cause) {
        super(message, cause);
    }
}
