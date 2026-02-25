package com.zhicore.comment.application.dto;

/**
 * 评论排序类型
 *
 * @author ZhiCore Team
 */
public enum CommentSortType {

    /**
     * 按时间排序（最新优先）
     */
    TIME,

    /**
     * 按热度排序（点赞数优先）
     */
    HOT
}
