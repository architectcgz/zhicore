package com.zhicore.common.exception;

import com.zhicore.common.result.ResultCode;
import lombok.Getter;

/**
 * 乐观锁并发冲突异常
 *
 * 约束：
 * - 必须映射为 HTTP 409（CONFLICT）
 * - 响应体需携带 retry_suggested=true，提示客户端可重试
 */
@Getter
public class OptimisticLockException extends BaseException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final boolean retrySuggested;

    public OptimisticLockException(String errorCode, String message, boolean retrySuggested) {
        super(ResultCode.CONFLICT, message);
        this.errorCode = errorCode;
        this.retrySuggested = retrySuggested;
    }

    public static OptimisticLockException concurrentUpdateConflict() {
        return new OptimisticLockException("CONCURRENT_UPDATE_CONFLICT", "并发更新冲突，请稍后重试", true);
    }

    public static OptimisticLockException concurrentTagUpdate() {
        return new OptimisticLockException("CONCURRENT_TAG_UPDATE", "并发标签更新冲突，请稍后重试", true);
    }
}
