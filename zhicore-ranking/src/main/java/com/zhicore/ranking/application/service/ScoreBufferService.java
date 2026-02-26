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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 分数缓冲服务
 *
 * <p>本地聚合热度更新，定时批量刷写到 Redis，减少网络开销。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>AtomicReference + ConcurrentHashMap：swap-and-flush 原子替换，消除竞态窗口</li>
 *   <li>ReentrantLock + Condition：单线程刷写协调，阈值触发仅唤醒刷写线程</li>
 *   <li>AtomicLong 事件计数器：记录累计事件数（非去重），阈值触发后重置</li>
 *   <li>刷写失败补偿：失败批次 merge 回当前活跃缓冲区，确保数据不丢失</li>
 * </ul>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
public class ScoreBufferService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingBufferProperties bufferProperties;

    /** 缓冲区引用，通过 AtomicReference.getAndSet 实现原子 swap */
    private final AtomicReference<ConcurrentHashMap<String, DoubleAdder>> bufferRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    /** 累计事件计数器（非去重），用于阈值触发刷写 */
    private final AtomicLong eventCounter = new AtomicLong(0);

    /** 刷写锁，保证同一时刻只有一个刷写操作在执行 */
    private final ReentrantLock flushLock = new ReentrantLock();
    private final Condition flushCondition = flushLock.newCondition();

    /** 是否已停止接收新事件（优雅停机用） */
    private volatile boolean stopped = false;

    // Micrometer 指标
    private final Counter flushSuccessCounter;
    private final Counter flushFailureCounter;
    private final Counter eventConsumeCounter;
    private final Timer flushTimer;

    public ScoreBufferService(RankingRedisRepository rankingRedisRepository,
                              RankingBufferProperties bufferProperties,
                              MeterRegistry meterRegistry) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.bufferProperties = bufferProperties;

        // 注册 Micrometer 指标
        this.flushSuccessCounter = Counter.builder("ranking.buffer.flush.total")
                .tag("result", "success")
                .description("缓冲区刷写成功次数")
                .register(meterRegistry);
        this.flushFailureCounter = Counter.builder("ranking.buffer.flush.total")
                .tag("result", "failure")
                .description("缓冲区刷写失败次数")
                .register(meterRegistry);
        this.eventConsumeCounter = Counter.builder("ranking.event.consume.total")
                .description("事件消费总数")
                .register(meterRegistry);
        this.flushTimer = Timer.builder("ranking.buffer.flush.duration")
                .description("缓冲区刷写耗时")
                .register(meterRegistry);

        // 注册缓冲区大小 Gauge
        meterRegistry.gauge("ranking.buffer.size", this, ScoreBufferService::getBufferSize);
    }

    /**
     * 添加分数到本地缓冲区
     *
     * @param entityType 实体类型（post, creator, topic）
     * @param entityId   实体ID
     * @param delta      分数增量（支持负值）
     */
    public void addScore(String entityType, String entityId, double delta) {
        if (stopped) {
            log.warn("缓冲区已停止接收新事件: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        String key = buildKey(entityType, entityId);
        bufferRef.get().computeIfAbsent(key, k -> new DoubleAdder()).add(delta);
        eventConsumeCounter.increment();

        long count = eventCounter.incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("添加分数到缓冲区: key={}, delta={}, eventCount={}", key, delta, count);
        }

        // 阈值触发：唤醒刷写线程
        if (count >= bufferProperties.getBatchSize()) {
            signalFlush();
        }
    }

    /**
     * 唤醒刷写线程（阈值触发时调用）
     */
    private void signalFlush() {
        if (flushLock.tryLock()) {
            try {
                flushCondition.signal();
            } finally {
                flushLock.unlock();
            }
        }
    }

    /**
     * 执行 swap-and-flush
     *
     * <p>通过 AtomicReference.getAndSet 原子替换缓冲区，新事件写入新 Map，不阻塞消费。
     * 刷写失败时将失败批次 merge 回当前活跃缓冲区。</p>
     *
     * @return 刷写的记录数
     */
    public int flush() {
        flushLock.lock();
        try {
            return doFlush();
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * 实际刷写逻辑（必须在持有 flushLock 时调用）
     */
    private int doFlush() {
        // 原子 swap：取出旧 Map，替换为新的空 Map
        ConcurrentHashMap<String, DoubleAdder> oldBuffer = bufferRef.getAndSet(new ConcurrentHashMap<>());
        eventCounter.set(0);

        if (oldBuffer.isEmpty()) {
            return 0;
        }

        // 将 DoubleAdder 转为 snapshot Map
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

        // 刷写失败补偿：将失败的条目 merge 回当前活跃缓冲区
        if (!failedEntries.isEmpty()) {
            log.warn("刷写部分失败，将 {} 条记录 merge 回缓冲区", failedEntries.size());
            ConcurrentHashMap<String, DoubleAdder> currentBuffer = bufferRef.get();
            failedEntries.forEach((key, delta) ->
                    currentBuffer.computeIfAbsent(key, k -> new DoubleAdder()).add(delta));
            flushFailureCounter.increment();
        }

        if (flushedCount > 0) {
            flushSuccessCounter.increment();
        }

        log.info("缓冲区刷写完成: 成功={}, 失败={}", flushedCount, failedEntries.size());
        return flushedCount;
    }

    /**
     * 停止接收新事件（优雅停机第一步）
     */
    public void stopAccepting() {
        this.stopped = true;
        log.info("缓冲区已停止接收新事件");
    }

    /**
     * 是否已停止
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 获取当前缓冲区大小（用于监控）
     */
    public int getBufferSize() {
        return bufferRef.get().size();
    }

    /**
     * 获取累计事件数
     */
    public long getEventCount() {
        return eventCounter.get();
    }

    /**
     * 清空缓冲区（用于测试）
     */
    public void clearBuffer() {
        bufferRef.set(new ConcurrentHashMap<>());
        eventCounter.set(0);
    }

    private String buildKey(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }
}
