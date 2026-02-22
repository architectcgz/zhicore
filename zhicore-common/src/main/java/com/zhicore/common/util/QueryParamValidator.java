package com.zhicore.common.util;

import com.zhicore.common.exception.ValidationException;

/**
 * 查询参数验证工具类
 * 用于验证和规范化管理查询接口的参数
 *
 * @author ZhiCore Team
 */
public class QueryParamValidator {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 验证并规范化页码
     * 如果页码无效（≤0），返回默认值
     *
     * @param page 页码
     * @return 规范化后的页码
     */
    public static int validatePage(int page) {
        return page > 0 ? page : DEFAULT_PAGE;
    }

    /**
     * 验证并规范化页面大小
     * 如果页面大小无效（≤0），返回默认值
     * 如果页面大小超过最大值，返回最大值
     *
     * @param size 页面大小
     * @return 规范化后的页面大小
     */
    public static int validateSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 验证并规范化关键词
     * - 如果关键词为 null 或空白，返回 null
     * - 如果关键词长度超过最大值，抛出异常
     * - 返回去除首尾空白的关键词
     *
     * @param keyword 关键词
     * @return 规范化后的关键词，如果为空则返回 null
     * @throws ValidationException 如果关键词长度超过最大值
     */
    public static String validateKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new ValidationException("关键词长度不能超过" + MAX_KEYWORD_LENGTH + "字符");
        }
        
        return trimmed;
    }

    /**
     * 验证并规范化状态参数
     * - 如果状态为 null 或空白，返回 null
     * - 返回去除首尾空白并转换为大写的状态
     *
     * @param status 状态
     * @return 规范化后的状态，如果为空则返回 null
     */
    public static String validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    /**
     * 验证并规范化ID参数
     * - 如果ID为 null 或空白，返回 null
     * - 返回去除首尾空白的ID
     *
     * @param id ID
     * @return 规范化后的ID，如果为空则返回 null
     */
    public static String validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return id.trim();
    }

    /**
     * 计算偏移量
     *
     * @param page 页码（从1开始）
     * @param size 页面大小
     * @return 偏移量
     */
    public static int calculateOffset(int page, int size) {
        return (page - 1) * size;
    }
}
