package com.zhicore.migration.service.cdc.store;

import java.util.Map;

public interface PostStatsCacheStore {
    void upsert(String postId, Map<String, Object> data);

    void delete(String postId);
}
