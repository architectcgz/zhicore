package com.zhicore.idgenerator.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级本地 ID 生成器。
 *
 * 在上游 id-generator 集群不可用时，为当前代理服务提供单实例兜底能力，
 * 用于恢复 Docker 压测环境中的基础写路径。
 */
final class LocalIdGenerator {

    private static final long EPOCH = 1704067200000L;
    private static final long WORKER_ID = 1L;
    private static final long DATACENTER_ID = 1L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = WORKER_ID_SHIFT + 5L;
    private static final long TIMESTAMP_SHIFT = DATACENTER_ID_SHIFT + 5L;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private final ConcurrentMap<String, AtomicLong> segmentCounters = new ConcurrentHashMap<>();

    private long sequence;
    private long lastTimestamp = -1L;

    synchronized long nextSnowflakeId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = waitUntilNextMillis(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (DATACENTER_ID << DATACENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    List<Long> nextSnowflakeIds(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(nextSnowflakeId());
        }
        return ids;
    }

    long nextSegmentId(String bizTag) {
        AtomicLong counter = segmentCounters.computeIfAbsent(
                bizTag,
                key -> new AtomicLong(System.currentTimeMillis() * 1000)
        );
        return counter.incrementAndGet();
    }

    private long waitUntilNextMillis(long currentTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= currentTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
