package com.zhicore.ranking.application.service;

import com.zhicore.ranking.infrastructure.config.RankingBufferProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 分数缓冲服务
 *
 * <p>本地聚合热度更新，定时批量刷写到 Redis，减少网络开销。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>AtomicReference + ConcurrentHashMap：swap-and-flush 原子替换，消除竞态窗口</li>
 *   <li>ReentrantLock：保证同一时刻只有一个刷写操作，阈值触发使用 tryLock 非阻塞尝试</li>
 *   <li>AtomicLong 事件计数器：记录累计事件数（非去重），阈值触发后重置</li>
 *   <li>刷写失败补偿：失败批次 merge 回当前活跃缓冲区，连续失败计数告警</li>
 * </ul>
 *
 * <p>TODO: 当前刷写目标为 Redis（ZINCRBY），架构文档要求刷写到 MongoDB（权威数据源），
 * Redis 排行数据由快照任务全量刷新。待 MongoDB 持久化层就绪后迁移（见架构文档 2.3 节）</p>
 *
 * <p>TODO: WAL 兜底机制（见架构文档 2.3 节）——连续失败 3 次后写入本地磁盘 WAL 文件，
 * 服务启动时自动恢复未完成的刷写</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
public class ScoreBufferService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingBufferProperties bufferProperties;
    private final MeterRegistry meterRegistry;

    /** 缓冲区引用，通过 AtomicReference.getAndSet 实现原子 swap */
    private final AtomicReference<ConcurrentHashMap<String, DoubleAdder>> bufferRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    /** 累计事件计数器（非去重），用于阈值触发刷写 */
    private final AtomicLong eventCounter = new AtomicLong(0);

    /** 刷写锁，保证同一时刻只有一个刷写操作在执行 */
    private final ReentrantLock flushLock = new ReentrantLock();

    /** 连续刷写失败计数器，用于告警和未来 WAL 触发 */
    private final AtomicInteger consecutiveFailureCount = new AtomicInteger(0);

    /** 是否已停止接收新事件（优雅停机用） */
    private volatile boolean stopped = false;

    /** 按 entityType 缓存的事件消费计数器，避免热路径上重复 builder().register() */
    private final Map<String, Counter> eventCounters = new ConcurrentHashMap<>();

    // Micrometer 指标
    private final Counter flushSuccessCounter;
    private final Counter flushFailureCounter;
    private final Timer flushTimer;

    public ScoreBufferService(RankingRedisRepository rankingRedisRepository,
                              RankingBufferProperties bufferProperties,
                              MeterRegistry meterRegistry) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.bufferProperties = bufferProperties;
        this.meterRegistry = meterRegistry;

        this.flushSuccessCounter = Counter.builder("ranking.buffer.flush.count")
                .tag("result", "success")
                .description("缓冲区刷写成功次数")
                .register(meterRegistry);
        this.flushFailureCounter = Counter.builder("ranking.buffer.flush.count")
                .tag("result", "failure")
                .description("缓冲区刷写失败次数")
                .register(meterRegistry);
        this.flushTimer = Timer.builder("ranking.buffer.flush.duration")
                .description("缓冲区刷写耗时")
                .register(meterRegistry);

        meterRegistry.gauge("ranking.buffer.size", this, ScoreBufferService::getBufferSize);
    }

    /**
     * 添加分数到本地缓冲区
     *
     * @param entityType 实体类型（post, creator, topic）
     * @param entityId   实体ID
     * @param delta      分数增量
     */
    public void addScore(String entityType, String entityId, double delta) {
        if (stopped) {
            log.warn("缓冲区已停止接收新事件: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        String key = buildKey(entityType, entityId);
        bufferRef.get().computeIfAbsent(key, k -> new DoubleAdder()).add(delta);

        // 按 entityType 打标签的事件消费计数（本地缓存 Counter 实例，避免热路径重复 register）
        eventCounters.computeIfAbsent(entityType, type ->
                Counter.builder("ranking.event.consume")
                        .tag("entityType", type)
                        .register(meterRegistry)
        ).increment();

        long count = eventCounter.incrementAndGet();

        // 阈值触发：tryLock 非阻塞尝试，避免阻塞 MQ 消费线程
        if (count >= bufferProperties.getBatchSize()) {
            if (flushLock.tryLock()) {
                try {
                    doFlush();
                } finally {
                    flushLock.unlock();
                }
            }
            // 获取不到锁说明已有刷写在执行，跳过即可
        }
    }

    /**
     * 执行 swap-and-flush
     *
     * @return 成功刷写的记录数
     */
    public int flush() {
        flushLock.lock();
        try {
            return doFlush();
        } finally {
            flushLock.unlock();
        }
    }

    private int doFlush() {
        ConcurrentHashMap<String, DoubleAdder> oldBuffer = bufferRef.getAndSet(new ConcurrentHashMap<>());
        eventCounter.set(0);

        if (oldBuffer.isEmpty()) {
            return 0;
        }

        Map<String, Double> snapshot = new HashMap<>();
        oldBuffer.forEach((key, adder) -> {
            double value = adder.sum();
            if (value != 0) {
                snapshot.put(key, value);
            }
        });

        if (snapshot.isEmpty()) {
            return 0;
        }

        return flushTimer.record(() -> doFlushSnapshot(snapshot));
    }

    /**
     * 将 snapshot 数据刷写到 Redis，失败时 merge 回缓冲区
     *
     * <p>TODO: 架构文档要求刷写到 MongoDB，当前暂写 Redis（见类注释）</p>
     */
    private int doFlushSnapshot(Map<String, Double> snapshot) {
        int flushedCount = 0;
        Map<String, Double> failedEntries = new HashMap<>();

        for (Map.Entry<String, Double> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            double delta = entry.getValue();

            String[] parts = key.split(":", 2);
            if (parts.length != 2) {
                log.warn("无效的缓冲区 key 格式: {}", key);
                continue;
            }

            String entityType = parts[0];
            String entityId = parts[1];

            try {
                switch (entityType) {
                    case "post" -> rankingRedisRepository.incrementPostScore(entityId, delta);
                    case "creator" -> rankingRedisRepository.incrementCreatorScore(entityId, delta);
                    case "topic" -> rankingRedisRepository.incrementTopicScore(Long.parseLong(entityId), delta);
                    default -> {
                        log.warn("未知的实体类型: {}", entityType);
                        continue;
                    }
                }
                flushedCount++;
            } catch (Exception e) {
                log.error("刷写单条记录失败: key={}, delta={}", key, delta, e);
                failedEntries.put(key, delta);
            }
        }

        // 失败补偿：merge 回当前活跃缓冲区
        if (!failedEntries.isEmpty()) {
            int failures = consecutiveFailureCount.incrementAndGet();
            log.warn("刷写部分失败，将 {} 条记录 merge 回缓冲区（连续失败 {} 次）",
                    failedEntries.size(), failures);
            if (failures >= 3) {
                log.error("连续刷写失败已达 {} 次，请检查 Redis 连接状态！" +
                        "（TODO: 触发 WAL 写入，见架构文档 2.3 节）", failures);
            }
            ConcurrentHashMap<String, DoubleAdder> currentBuffer = bufferRef.get();
            failedEntries.forEach((k, d) ->
                    currentBuffer.computeIfAbsent(k, x -> new DoubleAdder()).add(d));
            flushFailureCounter.increment();
        } else {
            consecutiveFailureCount.set(0);
        }

        if (flushedCount > 0) {
            flushSuccessCounter.increment();
        }

        log.info("缓冲区刷写完成: 成功={}, 失败={}", flushedCount, failedEntries.size());
        return flushedCount;
    }

    public void stopAccepting() {
        this.stopped = true;
        log.info("缓冲区已停止接收新事件");
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getBufferSize() {
        return bufferRef.get().size();
    }

    public long getEventCount() {
        return eventCounter.get();
    }

    public void clearBuffer() {
        bufferRef.set(new ConcurrentHashMap<>());
        eventCounter.set(0);
    }

    private String buildKey(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }
}
