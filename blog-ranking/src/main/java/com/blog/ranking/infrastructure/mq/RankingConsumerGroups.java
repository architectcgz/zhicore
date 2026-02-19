package com.blog.ranking.infrastructure.mq;

/**
 * 排行榜服务消费者组常量
 *
 * @author Blog Team
 */
public final class RankingConsumerGroups {

    private RankingConsumerGroups() {
    }

    /**
     * 文章浏览热度消费者组
     */
    public static final String POST_VIEWED_CONSUMER = "ranking-post-viewed-consumer";

    /**
     * 文章点赞热度消费者组
     */
    public static final String POST_LIKED_CONSUMER = "ranking-post-liked-consumer";

    /**
     * 文章取消点赞热度消费者组
     */
    public static final String POST_UNLIKED_CONSUMER = "ranking-post-unliked-consumer";

    /**
     * 评论创建热度消费者组
     */
    public static final String COMMENT_CREATED_CONSUMER = "ranking-comment-created-consumer";

    /**
     * 文章收藏热度消费者组
     */
    public static final String POST_FAVORITED_CONSUMER = "ranking-post-favorited-consumer";

    /**
     * 文章取消收藏热度消费者组
     */
    public static final String POST_UNFAVORITED_CONSUMER = "ranking-post-unfavorited-consumer";
}
