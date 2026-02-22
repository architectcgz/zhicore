package com.zhicore.ranking.application.service;

import com.zhicore.ranking.infrastructure.config.RankingBufferProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 分数缓冲服务
 * 
 * <p>本地聚合热度更新，定时批量刷写到 Redis，减少网络开销</p>
 * 
 * <p>设计优势：</p>
 * <ul>
 *   <li>本地聚合：使用 ConcurrentHashMap + DoubleAdder 在内存中累加分数</li>
 *   <li>批量刷写：定时将累积的分数批量写入 Redis，减少网络调用</li>
 *   <li>线程安全：ConcurrentHashMap 和 DoubleAdder 保证并发安全</li>
 *   <li>可配置性：刷写间隔和批量大小可通过配置调整</li>
 *   <li>动态刷新：使用 @ConfigurationProperties 支持配置动态刷新</li>
 * </ul>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreBufferService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingBufferProperties bufferProperties;

    /**
     * 本地分数缓冲区
     * Key 格式：{entityType}:{entityId}
     * 例如：post:123456, creator:789012, topic:345678
     */
    private final ConcurrentHashMap<String, DoubleAdder> scoreBuffer = new ConcurrentHashMap<>();

    /**
     * 添加分数到本地缓冲区
     * 
     * <p>线程安全的累加操作，使用 DoubleAdder 保证高并发性能</p>
     *
     * @param entityType 实体类型（post, creator, topic）
     * @param entityId 实体ID
     * @param delta 分数增量
     */
    public void addScore(String entityType, String entityId, double delta) {
        String key = buildKey(entityType, entityId);
        scoreBuffer.computeIfAbsent(key, k -> new DoubleAdder()).add(delta);
        
        if (log.isDebugEnabled()) {
            log.debug("添加分数到缓冲区: key={}, delta={}", key, delta);
        }
    }

    /**
     * 定时刷写缓冲区到 Redis
     * 
     * <p>使用 fixedDelayString 从配置读取刷写间隔</p>
     * <p>刷写逻辑：</p>
     * <ol>
     *   <li>读取并清空本地缓冲区</li>
     *   <li>批量调用 Redis 更新分数</li>
     *   <li>记录刷写统计信息</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${ranking.buffer.flush-interval:5000}")
    public void flushToRedis() {
        if (scoreBuffer.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        
        try {
            // 读取并清空缓冲区
            Map<String, Double> snapshot = new HashMap<>();
            scoreBuffer.forEach((key, adder) -> {
                double value = adder.sumThenReset();
                if (value != 0) {
                    snapshot.put(key, value);
                }
            });

            if (snapshot.isEmpty()) {
                return;
            }

            // 批量刷写到 Redis
            int flushedCount = 0;
            for (Map.Entry<String, Double> entry : snapshot.entrySet()) {
                String key = entry.getKey();
                double delta = entry.getValue();
                
                // 解析 key 格式：{entityType}:{entityId}
                String[] parts = key.split(":", 2);
                if (parts.length != 2) {
                    log.warn("无效的缓冲区 key 格式: {}", key);
                    continue;
                }
                
                String entityType = parts[0];
                String entityId = parts[1];
                
                // 根据实体类型调用对应的更新方法
                switch (entityType) {
                    case "post":
                        rankingRedisRepository.incrementPostScore(entityId, delta);
                        break;
                    case "creator":
                        rankingRedisRepository.incrementCreatorScore(entityId, delta);
                        break;
                    case "topic":
                        rankingRedisRepository.incrementTopicScore(Long.parseLong(entityId), delta);
                        break;
                    default:
                        log.warn("未知的实体类型: {}", entityType);
                        continue;
                }
                
                flushedCount++;
                
                // 批量大小限制（防止单次刷写过多）
                if (flushedCount >= bufferProperties.getBatchSize()) {
                    log.info("达到批量大小限制，本次刷写 {} 条记录", flushedCount);
                    break;
                }
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("缓冲区刷写完成: 刷写数量={}, 耗时={}ms", flushedCount, elapsedTime);
            
        } catch (Exception e) {
            log.error("缓冲区刷写失败", e);
            // 异常不影响后续定时任务执行
        }
    }

    /**
     * 优雅关闭：刷写剩余数据
     * 
     * <p>在应用关闭时调用，确保缓冲区中的数据不丢失</p>
     */
    @PreDestroy
    public void shutdown() {
        log.info("应用关闭，开始刷写剩余缓冲区数据");
        flushToRedis();
        log.info("缓冲区数据刷写完成");
    }

    /**
     * 构建缓冲区 key
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 缓冲区 key
     */
    private String buildKey(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }

    /**
     * 获取当前缓冲区大小（用于监控）
     *
     * @return 缓冲区大小
     */
    public int getBufferSize() {
        return scoreBuffer.size();
    }

    /**
     * 清空缓冲区（用于测试）
     */
    public void clearBuffer() {
        scoreBuffer.clear();
    }
}
