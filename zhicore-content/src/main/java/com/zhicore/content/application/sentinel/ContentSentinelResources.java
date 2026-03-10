package com.zhicore.content.application.sentinel;

/**
 * 文章服务 Sentinel 方法级资源常量。
 */
public final class ContentSentinelResources {

    private ContentSentinelResources() {
    }

    public static final String GET_POST_DETAIL = "content:getPostDetail";
    public static final String GET_POST_LIST = "content:getPostList";
    public static final String GET_POST_CONTENT = "content:getPostContent";
    public static final String GET_TAG_DETAIL = "content:getTagDetail";
    public static final String LIST_TAGS = "content:listTags";
    public static final String SEARCH_TAGS = "content:searchTags";
    public static final String GET_POSTS_BY_TAG = "content:getPostsByTag";
    public static final String GET_HOT_TAGS = "content:getHotTags";
    public static final String IS_POST_LIKED = "content:isPostLiked";
    public static final String BATCH_CHECK_POST_LIKED = "content:batchCheckPostLiked";
    public static final String GET_POST_LIKE_COUNT = "content:getPostLikeCount";
    public static final String IS_POST_FAVORITED = "content:isPostFavorited";
    public static final String BATCH_CHECK_POST_FAVORITED = "content:batchCheckPostFavorited";
    public static final String GET_POST_FAVORITE_COUNT = "content:getPostFavoriteCount";
    public static final String ADMIN_QUERY_POSTS = "content:adminQueryPosts";
    public static final String LIST_FAILED_OUTBOX = "content:listFailedOutbox";
}
