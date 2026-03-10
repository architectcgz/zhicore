package com.zhicore.comment.application.sentinel;

/**
 * 评论服务 Sentinel 方法级资源常量。
 */
public final class CommentSentinelResources {

    private CommentSentinelResources() {
    }

    public static final String GET_COMMENT_DETAIL = "comment:getCommentDetail";
    public static final String GET_TOP_LEVEL_COMMENTS_PAGE = "comment:getTopLevelCommentsPage";
    public static final String GET_TOP_LEVEL_COMMENTS_CURSOR = "comment:getTopLevelCommentsCursor";
    public static final String GET_REPLIES_PAGE = "comment:getRepliesPage";
    public static final String GET_REPLIES_CURSOR = "comment:getRepliesCursor";
    public static final String IS_COMMENT_LIKED = "comment:isCommentLiked";
    public static final String BATCH_CHECK_COMMENT_LIKED = "comment:batchCheckCommentLiked";
    public static final String GET_COMMENT_LIKE_COUNT = "comment:getCommentLikeCount";
    public static final String ADMIN_QUERY_COMMENTS = "comment:adminQueryComments";
}
