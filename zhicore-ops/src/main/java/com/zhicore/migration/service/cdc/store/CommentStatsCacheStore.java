package com.zhicore.migration.service.cdc.store;

import java.util.Map;

public interface CommentStatsCacheStore {
    void upsert(String commentId, Map<String, Object> data);

    void delete(String commentId);
}
