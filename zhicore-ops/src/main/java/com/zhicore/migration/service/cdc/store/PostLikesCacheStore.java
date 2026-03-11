package com.zhicore.migration.service.cdc.store;

public interface PostLikesCacheStore {
    void addLike(String postId, String userId);

    void removeLike(String postId, String userId);
}
