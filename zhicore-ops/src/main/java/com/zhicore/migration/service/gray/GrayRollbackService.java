package com.zhicore.migration.service.gray;

import com.zhicore.migration.infrastructure.config.GrayReleaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 灰度回滚服务
 * 提供快速回滚能力
 */
@Slf4j
@RequiredArgsConstructor
public class GrayRollbackService {

    private final RedissonClient redissonClient;
    private final GrayReleaseProperties properties;

    private static final String GRAY_CONFIG_KEY = "gray:config";
    private static final String GRAY_STATUS_KEY = "gray:status";
    private static final String ROLLBACK_HISTORY_KEY = "gray:rollback:history";

    /**
     * 执行快速回滚
     * 将所有流量切回旧系统
     */
    public RollbackResult rollback(String reason) {
        log.warn("开始执行灰度回滚, 原因: {}", reason);

        RollbackResult result = RollbackResult.builder()
                .timestamp(System.currentTimeMillis())
                .reason(reason)
                .build();

        try {
            // 1. 更新灰度配置，禁用灰度
            GrayConfig config = getConfig();
            if (config != null) {
                config.setEnabled(false);
                config.setTrafficRatio(0);
                updateConfig(config);
            }

            // 2. 更新灰度状态
            GrayStatus status = getStatus();
            if (status != null) {
                status.setPhase(GrayPhase.ROLLED_BACK);
                status.setCurrentRatio(0);
                status.setLastUpdateTime(System.currentTimeMillis());
                updateStatus(status);
            }

            // 3. 清除所有用户的灰度标记
            clearAllUserGrayFlags();

            // 4. 记录回滚历史
            saveRollbackHistory(result);

            result.setSuccess(true);
            log.info("灰度回滚完成");

        } catch (Exception e) {
            log.error("灰度回滚失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * 推进灰度阶段
     * 逐步增加灰度流量比例
     */
    public AdvanceResult advancePhase() {
        log.info("开始推进灰度阶段");

        AdvanceResult result = AdvanceResult.builder()
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            GrayStatus status = getStatus();
            if (status == null) {
                result.setSuccess(false);
                result.setErrorMessage("灰度状态不存在");
                return result;
            }

            GrayPhase currentPhase = status.getPhase();
            if (!currentPhase.canAdvance()) {
                result.setSuccess(false);
                result.setErrorMessage("当前阶段无法推进: " + currentPhase);
                return result;
            }

            // 推进到下一阶段
            GrayPhase nextPhase = currentPhase.next();
            status.setPhase(nextPhase);
            status.setCurrentRatio(nextPhase.getRatio());
            status.setLastUpdateTime(System.currentTimeMillis());
            updateStatus(status);

            // 更新配置
            GrayConfig config = getConfig();
            if (config != null) {
                config.setTrafficRatio(nextPhase.getRatio());
                updateConfig(config);
            }

            // 清除用户灰度标记，让用户重新分配
            clearAllUserGrayFlags();

            result.setSuccess(true);
            result.setPreviousPhase(currentPhase);
            result.setCurrentPhase(nextPhase);
            result.setCurrentRatio(nextPhase.getRatio());

            log.info("灰度阶段推进完成: {} -> {}, 流量比例: {}%", 
                    currentPhase, nextPhase, nextPhase.getRatio());

        } catch (Exception e) {
            log.error("灰度阶段推进失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * 检查是否需要自动回滚
     * 基于错误率和延迟阈值
     */
    public boolean shouldAutoRollback() {
        GrayStatus status = getStatus();
        if (status == null) {
            return false;
        }

        // 检查错误率
        if (status.getTotalRequests() > 0) {
            double errorRate = (double) status.getErrorCount() / status.getTotalRequests();
            if (errorRate > properties.getAlert().getErrorRateThreshold()) {
                log.warn("错误率超过阈值: {}% > {}%", 
                        errorRate * 100, properties.getAlert().getErrorRateThreshold() * 100);
                return true;
            }
        }

        // 检查延迟
        if (status.getAvgLatencyMs() > properties.getAlert().getLatencyThresholdMs()) {
            log.warn("平均延迟超过阈值: {}ms > {}ms", 
                    status.getAvgLatencyMs(), properties.getAlert().getLatencyThresholdMs());
            return true;
        }

        return false;
    }

    private GrayConfig getConfig() {
        RBucket<GrayConfig> bucket = redissonClient.getBucket(GRAY_CONFIG_KEY);
        return bucket.get();
    }

    private void updateConfig(GrayConfig config) {
        RBucket<GrayConfig> bucket = redissonClient.getBucket(GRAY_CONFIG_KEY);
        bucket.set(config, Duration.ofDays(7));
    }

    private GrayStatus getStatus() {
        RBucket<GrayStatus> bucket = redissonClient.getBucket(GRAY_STATUS_KEY);
        return bucket.get();
    }

    private void updateStatus(GrayStatus status) {
        RBucket<GrayStatus> bucket = redissonClient.getBucket(GRAY_STATUS_KEY);
        bucket.set(status, Duration.ofDays(7));
    }

    private void clearAllUserGrayFlags() {
        redissonClient.getKeys().deleteByPattern("gray:user:*");
    }

    private void saveRollbackHistory(RollbackResult result) {
        redissonClient.getList(ROLLBACK_HISTORY_KEY).add(result);
    }

    /**
     * 回滚结果
     */
    @lombok.Data
    @lombok.Builder
    public static class RollbackResult {
        private long timestamp;
        private boolean success;
        private String reason;
        private String errorMessage;
    }

    /**
     * 阶段推进结果
     */
    @lombok.Data
    @lombok.Builder
    public static class AdvanceResult {
        private long timestamp;
        private boolean success;
        private GrayPhase previousPhase;
        private GrayPhase currentPhase;
        private int currentRatio;
        private String errorMessage;
    }
}
