package com.zhicore.message.infrastructure.cache;

import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.application.model.ImMessageMapping;
import com.zhicore.message.application.port.store.ImMessageBridgeStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 IM 桥接映射存储。
 */
@Component
@RequiredArgsConstructor
public class RedisImMessageBridgeStore implements ImMessageBridgeStore {

    private final RedissonClient redissonClient;

    @Override
    public void save(Long localMessageId, ImMessageMapping mapping, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(MessageRedisKeys.imBridgeMapping(localMessageId));
        bucket.set(JsonUtils.toJson(mapping), ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Optional<ImMessageMapping> find(Long localMessageId) {
        RBucket<String> bucket = redissonClient.getBucket(MessageRedisKeys.imBridgeMapping(localMessageId));
        return Optional.ofNullable(bucket.get()).map(json -> JsonUtils.fromJson(json, ImMessageMapping.class));
    }
}
