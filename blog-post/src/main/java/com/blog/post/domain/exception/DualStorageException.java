package com.blog.post.domain.exception;

/**
 * 双存储异常
 * 当 PostgreSQL 和 MongoDB 之间的数据操作失败时抛出
 *
 * @author Blog Team
 */
public class DualStorageException extends RuntimeException {

    public DualStorageException(String message) {
        super(message);
    }

    public DualStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
