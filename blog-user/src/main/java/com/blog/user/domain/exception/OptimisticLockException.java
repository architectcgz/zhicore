package com.blog.user.domain.exception;

/**
 * 乐观锁异常
 * 
 * 当并发更新导致版本号冲突时抛出此异常
 * 
 * 使用场景：
 * - 用户资料并发更新
 * - 需要保证版本号单调递增的场景
 * 
 * @author Blog Team
 */
public class OptimisticLockException extends RuntimeException {
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     */
    public OptimisticLockException(String message) {
        super(message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 原因
     */
    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
