package com.zhicore.migration.service.gray.store;

import com.zhicore.migration.service.gray.GrayConfig;
import com.zhicore.migration.service.gray.GrayDataReconciliationTask;
import com.zhicore.migration.service.gray.GrayRollbackService;
import com.zhicore.migration.service.gray.GrayStatus;

/**
 * 灰度发布链路的状态存储抽象。
 */
public interface GrayReleaseStore {

    /**
     * 读取当前灰度配置。
     */
    GrayConfig getConfig();

    /**
     * 持久化灰度配置更新。
     */
    void saveConfig(GrayConfig config);

    /**
     * 初始化灰度配置。
     */
    void initializeConfig(GrayConfig config);

    /**
     * 读取当前灰度状态。
     */
    GrayStatus getStatus();

    /**
     * 持久化灰度状态更新。
     */
    void saveStatus(GrayStatus status);

    /**
     * 初始化灰度状态，仅在不存在时写入。
     */
    void initializeStatusIfAbsent(GrayStatus status);

    /**
     * 读取用户灰度标记。
     */
    Boolean getUserGrayFlag(String userId);

    /**
     * 保存用户灰度标记。
     */
    void saveUserGrayFlag(String userId, boolean isGray);

    /**
     * 清除指定用户灰度标记。
     */
    void clearUserGrayFlag(String userId);

    /**
     * 清除全部用户灰度标记。
     */
    void clearAllUserGrayFlags();

    /**
     * 保存最新对账结果并写入历史。
     */
    void saveReconciliationResult(GrayDataReconciliationTask.ReconciliationResult result);

    /**
     * 读取最新对账结果。
     */
    GrayDataReconciliationTask.ReconciliationResult getLatestReconciliationResult();

    /**
     * 追加一条回滚历史。
     */
    void saveRollbackHistory(GrayRollbackService.RollbackResult result);
}
