package com.blog.search.infrastructure.mq;

/**
 * 搜索服务消费者组常量
 *
 * @author Blog Team
 */
public final class SearchConsumerGroups {

    private SearchConsumerGroups() {
    }

    /**
     * 文章发布索引消费者组
     */
    public static final String POST_PUBLISHED_CONSUMER = "search-post-published-consumer";

    /**
     * 文章更新索引消费者组
     */
    public static final String POST_UPDATED_CONSUMER = "search-post-updated-consumer";

    /**
     * 文章删除索引消费者组
     */
    public static final String POST_DELETED_CONSUMER = "search-post-deleted-consumer";
    
    /**
     * 文章标签更新索引消费者组
     */
    public static final String POST_TAGS_UPDATED_CONSUMER = "search-post-tags-updated-consumer";
}
