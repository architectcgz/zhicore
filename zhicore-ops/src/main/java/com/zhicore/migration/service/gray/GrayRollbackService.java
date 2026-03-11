package com.zhicore.migration.service.gray;

import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度回滚服务
 * 提供快速回滚能力
 */
@Slf4j
@RequiredArgsConstructor
public class GrayRollbackService {

    private final GrayReleaseStore store;
    private final GrayReleaseSettings settings;

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
            store.saveRollbackHistory(result);

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
            if (errorRate > settings.alert().errorRateThreshold()) {
                log.warn("错误率超过阈值: {}% > {}%", 
                        errorRate * 100, settings.alert().errorRateThreshold() * 100);
                return true;
            }
        }

        // 检查延迟
        if (status.getAvgLatencyMs() > settings.alert().latencyThresholdMs()) {
            log.warn("平均延迟超过阈值: {}ms > {}ms", 
                    status.getAvgLatencyMs(), settings.alert().latencyThresholdMs());
            return true;
        }

        return false;
    }

    private GrayConfig getConfig() {
        return store.getConfig();
    }

    private void updateConfig(GrayConfig config) {
        store.saveConfig(config);
    }

    private GrayStatus getStatus() {
        return store.getStatus();
    }

    private void updateStatus(GrayStatus status) {
        store.saveStatus(status);
    }

    private void clearAllUserGrayFlags() {
        store.clearAllUserGrayFlags();
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
