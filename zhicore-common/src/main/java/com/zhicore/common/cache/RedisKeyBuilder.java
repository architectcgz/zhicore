package com.zhicore.common.cache;

/**
 * Redis Key 构建器
 * 
 * 遵循命名规范：{service}:{entity}:{id}:{field}
 *
 * @author ZhiCore Team
 */
public class RedisKeyBuilder {

    private final StringBuilder keyBuilder;

    private RedisKeyBuilder(String prefix) {
        this.keyBuilder = new StringBuilder(prefix);
    }

    /**
     * 创建构建器
     *
     * @param service 服务名称
     * @return 构建器
     */
    public static RedisKeyBuilder of(String service) {
        return new RedisKeyBuilder(service);
    }

    /**
     * 添加实体类型
     */
    public RedisKeyBuilder entity(String entity) {
        keyBuilder.append(CacheConstants.SEPARATOR).append(entity);
        return this;
    }

    /**
     * 添加 ID
     */
    public RedisKeyBuilder id(Object id) {
        keyBuilder.append(CacheConstants.SEPARATOR).append(id);
        return this;
    }

    /**
     * 添加字段
     */
    public RedisKeyBuilder field(String field) {
        keyBuilder.append(CacheConstants.SEPARATOR).append(field);
        return this;
    }

    /**
     * 添加自定义部分
     */
    public RedisKeyBuilder append(String part) {
        keyBuilder.append(CacheConstants.SEPARATOR).append(part);
        return this;
    }

    /**
     * 构建 Key
     */
    public String build() {
        return keyBuilder.toString();
    }

    @Override
    public String toString() {
        return build();
    }

    // ==================== 便捷方法 ====================

    /**
     * 构建用户相关 Key
     */
    public static class User {
        public static String detail(String userId) {
            return RedisKeyBuilder.of(CacheConstants.USER_PREFIX)
                    .id(userId)
                    .build();
        }

        public static String followersCount(String userId) {
            return RedisKeyBuilder.of(CacheConstants.USER_PREFIX)
                    .id(userId)
                    .field("followers_count")
                    .build();
        }

        public static String followingCount(String userId) {
            return RedisKeyBuilder.of(CacheConstants.USER_PREFIX)
                    .id(userId)
                    .field("following_count")
                    .build();
        }

        public static String checkInBitmap(String userId, int yearMonth) {
            return RedisKeyBuilder.of(CacheConstants.USER_PREFIX)
                    .id(userId)
                    .field("checkin")
                    .append(String.valueOf(yearMonth))
                    .build();
        }
    }

    /**
     * 构建文章相关 Key
     */
    public static class Post {
        public static String detail(String postId) {
            return RedisKeyBuilder.of(CacheConstants.POST_PREFIX)
                    .id(postId)
                    .build();
        }

        public static String likeCount(String postId) {
            return RedisKeyBuilder.of(CacheConstants.POST_PREFIX)
                    .id(postId)
                    .field("like_count")
                    .build();
        }

        public static String viewCount(String postId) {
            return RedisKeyBuilder.of(CacheConstants.POST_PREFIX)
                    .id(postId)
                    .field("view_count")
                    .build();
        }

        public static String userLiked(String userId, String postId) {
            return RedisKeyBuilder.of(CacheConstants.POST_PREFIX)
                    .append("like")
                    .id(postId)
                    .append(userId)
                    .build();
        }

        public static String userFavorited(String userId, String postId) {
            return RedisKeyBuilder.of(CacheConstants.POST_PREFIX)
                    .append("favorite")
                    .id(postId)
                    .append(userId)
                    .build();
        }
    }

    /**
     * 构建评论相关 Key
     */
    public static class Comment {
        public static String detail(String commentId) {
            return RedisKeyBuilder.of(CacheConstants.COMMENT_PREFIX)
                    .id(commentId)
                    .build();
        }

        public static String likeCount(String commentId) {
            return RedisKeyBuilder.of(CacheConstants.COMMENT_PREFIX)
                    .id(commentId)
                    .field("like_count")
                    .build();
        }

        public static String postComments(String postId) {
            return RedisKeyBuilder.of(CacheConstants.COMMENT_PREFIX)
                    .append("post")
                    .id(postId)
                    .build();
        }
    }

    /**
     * 构建锁相关 Key
     */
    public static class Lock {
        public static String forEntity(String entity, Object id) {
            return RedisKeyBuilder.of(CacheConstants.LOCK_PREFIX)
                    .entity(entity)
                    .id(id)
                    .build();
        }
    }
}
