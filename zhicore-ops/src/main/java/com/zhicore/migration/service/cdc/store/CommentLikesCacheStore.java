package com.zhicore.migration.service.cdc.store;

public interface CommentLikesCacheStore {
    void addLike(String commentId, String userId);

    void removeLike(String commentId, String userId);
}
