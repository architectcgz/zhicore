package com.zhicore.idgenerator.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.idgenerator.service.IdGeneratorService;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelHandlers;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelResources;
import com.platform.idgen.client.IdGeneratorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ID生成服务实现
 * 
 * 通过id-generator-client调用底层ID生成服务
 */
@Slf4j
@Service
public class IdGeneratorServiceImpl implements IdGeneratorService {

    /** 批量生成 ID 的最大数量 */
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int DEFAULT_SNOWFLAKE_CACHE_CAPACITY = 512;
    private static final int DEFAULT_SNOWFLAKE_REFILL_THRESHOLD = 128;
    private static final int DEFAULT_SNOWFLAKE_PREFETCH_BATCH_SIZE = 256;

    private final IdGeneratorClient idGeneratorClient;
    private final LocalIdGenerator localIdGenerator;
    private final LinkedBlockingQueue<Long> snowflakeIdCache;
    private final ReentrantLock snowflakePrefetchLock = new ReentrantLock();
    private final int snowflakeRefillThreshold;
    private final int snowflakePrefetchBatchSize;

    public IdGeneratorServiceImpl(IdGeneratorClient idGeneratorClient) {
        this(
                idGeneratorClient,
                DEFAULT_SNOWFLAKE_CACHE_CAPACITY,
                DEFAULT_SNOWFLAKE_REFILL_THRESHOLD,
                DEFAULT_SNOWFLAKE_PREFETCH_BATCH_SIZE
        );
    }

    @Autowired
    IdGeneratorServiceImpl(
            IdGeneratorClient idGeneratorClient,
            @Value("${id-generator.proxy-cache.snowflake-capacity:512}") int snowflakeCacheCapacity,
            @Value("${id-generator.proxy-cache.snowflake-refill-threshold:128}") int snowflakeRefillThreshold,
            @Value("${id-generator.proxy-cache.snowflake-prefetch-batch-size:256}") int snowflakePrefetchBatchSize
    ) {
        this.idGeneratorClient = idGeneratorClient;
        this.localIdGenerator = new LocalIdGenerator();
        this.snowflakeIdCache = new LinkedBlockingQueue<>(normalizeSnowflakeCacheCapacity(snowflakeCacheCapacity));
        this.snowflakeRefillThreshold = normalizeSnowflakeRefillThreshold(snowflakeRefillThreshold, this.snowflakeIdCache.remainingCapacity());
        this.snowflakePrefetchBatchSize = normalizeSnowflakePrefetchBatchSize(snowflakePrefetchBatchSize, this.snowflakeIdCache.remainingCapacity());
    }

    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateSnowflakeIdBlocked"
    )
    public Long generateSnowflakeId() {
        try {
            Long cachedId = snowflakeIdCache.poll();
            if (cachedId != null) {
                triggerSnowflakeCacheRefillIfNeeded();
                log.debug("成功从本地预取缓存生成Snowflake ID: {}", cachedId);
                return cachedId;
            }

            refillSnowflakeCacheBlocking();

            Long refilledId = snowflakeIdCache.poll();
            if (refilledId == null) {
                return fallbackSnowflakeId("Snowflake 本地预取缓存补货后仍为空", null);
            }

            triggerSnowflakeCacheRefillIfNeeded();
            log.debug("成功通过预取缓存生成Snowflake ID: {}", refilledId);
            return refilledId;
        } catch (Exception e) {
            return fallbackSnowflakeId("上游 Snowflake 服务不可用", e);
        }
    }
    
    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_BATCH_SNOWFLAKE_IDS,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateBatchSnowflakeIdsBlocked"
    )
    public List<Long> generateBatchSnowflakeIds(int count) {
        // 参数验证
        if (count <= 0 || count > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("生成数量必须在1-" + MAX_BATCH_SIZE + "之间");
        }
        
        try {
            // 使用 IdGeneratorClient 的批量接口，更高效
            List<Long> ids = idGeneratorClient.nextSnowflakeIds(count);
            if (ids == null || ids.isEmpty()) {
                return fallbackBatchSnowflakeIds(count, "上游返回空批次", null);
            }
            if (ids.size() != count) {
                return fallbackBatchSnowflakeIds(
                        count,
                        "上游返回数量不匹配，期望" + count + "个，实际" + ids.size() + "个",
                        null
                );
            }
            log.debug("成功批量生成{}个Snowflake ID", count);
            return ids;
        } catch (IllegalArgumentException e) {
            // 参数异常直接抛出
            throw e;
        } catch (Exception e) {
            return fallbackBatchSnowflakeIds(count, "上游批量 Snowflake 服务不可用", e);
        }
    }
    
    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_SEGMENT_ID,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateSegmentIdBlocked"
    )
    public Long generateSegmentId(String bizTag) {
        // 参数验证
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("业务标签不能为空");
        }
        
        try {
            Long id = idGeneratorClient.nextSegmentId(bizTag);
            if (id == null) {
                return fallbackSegmentId(bizTag, "上游返回空 Segment ID", null);
            }
            log.debug("成功生成Segment ID: bizTag={}, id={}", bizTag, id);
            return id;
        } catch (IllegalArgumentException e) {
            // 参数异常直接抛出
            throw e;
        } catch (Exception e) {
            return fallbackSegmentId(bizTag, "上游 Segment 服务不可用", e);
        }
    }

    private Long fallbackSnowflakeId(String reason, Exception cause) {
        Long localId = localIdGenerator.nextSnowflakeId();
        log.warn("Snowflake ID 生成降级到本地兜底: reason={}, localId={}", reason, localId, cause);
        return localId;
    }

    private List<Long> fallbackBatchSnowflakeIds(int count, String reason, Exception cause) {
        List<Long> localIds = localIdGenerator.nextSnowflakeIds(count);
        log.warn("批量 Snowflake ID 生成降级到本地兜底: reason={}, count={}", reason, count, cause);
        return localIds;
    }

    private Long fallbackSegmentId(String bizTag, String reason, Exception cause) {
        Long localId = localIdGenerator.nextSegmentId(bizTag);
        log.warn("Segment ID 生成降级到本地兜底: reason={}, bizTag={}, localId={}", reason, bizTag, localId, cause);
        return localId;
    }

    private void refillSnowflakeCacheBlocking() {
        snowflakePrefetchLock.lock();
        try {
            if (!snowflakeIdCache.isEmpty()) {
                return;
            }
            prefetchSnowflakeIds();
        } finally {
            snowflakePrefetchLock.unlock();
        }
    }

    private void triggerSnowflakeCacheRefillIfNeeded() {
        if (snowflakeIdCache.size() > snowflakeRefillThreshold) {
            return;
        }
        if (!snowflakePrefetchLock.tryLock()) {
            return;
        }
        try {
            if (snowflakeIdCache.size() <= snowflakeRefillThreshold) {
                prefetchSnowflakeIds();
            }
        } catch (Exception e) {
            log.warn("Snowflake 本地预取缓存补货失败，将继续使用现有缓存或兜底生成", e);
        } finally {
            snowflakePrefetchLock.unlock();
        }
    }

    private void prefetchSnowflakeIds() {
        int fetchCount = Math.min(snowflakePrefetchBatchSize, snowflakeIdCache.remainingCapacity());
        if (fetchCount <= 0) {
            return;
        }

        List<Long> prefetchedIds = idGeneratorClient.nextSnowflakeIds(fetchCount);
        if (prefetchedIds == null || prefetchedIds.isEmpty()) {
            throw new IllegalStateException("上游返回空 Snowflake 批次");
        }

        int addedCount = 0;
        for (Long prefetchedId : prefetchedIds) {
            if (prefetchedId != null && snowflakeIdCache.offer(prefetchedId)) {
                addedCount++;
            }
        }

        if (addedCount == 0) {
            throw new IllegalStateException("Snowflake 预取批次未成功写入本地缓存");
        }

        if (addedCount < fetchCount) {
            log.warn("Snowflake 预取批次不足: expected={}, actualAdded={}", fetchCount, addedCount);
        } else {
            log.debug("Snowflake 本地预取缓存补货完成: added={}, cacheSize={}", addedCount, snowflakeIdCache.size());
        }
    }

    private static int normalizeSnowflakeCacheCapacity(int configuredCapacity) {
        return Math.max(1, Math.min(MAX_BATCH_SIZE, configuredCapacity));
    }

    private static int normalizeSnowflakeRefillThreshold(int configuredThreshold, int cacheCapacity) {
        return Math.max(0, Math.min(configuredThreshold, Math.max(0, cacheCapacity - 1)));
    }

    private static int normalizeSnowflakePrefetchBatchSize(int configuredBatchSize, int cacheCapacity) {
        return Math.max(1, Math.min(Math.min(MAX_BATCH_SIZE, cacheCapacity), configuredBatchSize));
    }
}
