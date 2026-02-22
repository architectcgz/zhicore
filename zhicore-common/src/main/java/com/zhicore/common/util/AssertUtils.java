package com.zhicore.common.util;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.ResultCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 断言工具类
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    /**
     * 断言对象不为空
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言对象不为空
     */
    public static void notNull(Object object, ResultCode resultCode) {
        if (object == null) {
            throw new BusinessException(resultCode);
        }
    }

    /**
     * 断言资源存在
     */
    public static void notNullResource(Object object, String resourceType, Object resourceId) {
        if (object == null) {
            throw new ResourceNotFoundException(resourceType, resourceId);
        }
    }

    /**
     * 断言字符串不为空
     */
    public static void hasText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言集合不为空
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言条件为真
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言条件为真
     */
    public static void isTrue(boolean expression, ResultCode resultCode) {
        if (!expression) {
            throw new BusinessException(resultCode);
        }
    }

    /**
     * 断言条件为假
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }

    /**
     * 断言条件为假
     */
    public static void isFalse(boolean expression, ResultCode resultCode) {
        if (expression) {
            throw new BusinessException(resultCode);
        }
    }
}
