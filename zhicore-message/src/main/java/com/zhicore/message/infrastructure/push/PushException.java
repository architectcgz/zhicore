package com.zhicore.message.infrastructure.push;

/**
 * 推送异常
 *
 * @author ZhiCore Team
 */
public class PushException extends RuntimeException {

    public PushException(String message) {
        super(message);
    }

    public PushException(String message, Throwable cause) {
        super(message, cause);
    }
}
