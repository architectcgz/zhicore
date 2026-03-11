package com.zhicore.migration.service.cdc.store;

import java.util.Map;

public interface UserFollowStatsCacheStore {
    void upsert(String userId, Map<String, Object> data);

    void delete(String userId);
}
