package com.zhicore.migration.service.gray;

import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度路由决策器
 * 决定请求是否应该路由到灰度环境
 */
@Slf4j
@RequiredArgsConstructor
public class GrayRouter {

    private final GrayReleaseSettings settings;
    private final GrayReleaseStore store;

    /**
     * 判断用户是否应该进入灰度
     *
     * @param userId 用户ID
     * @return true 表示应该路由到灰度环境
     */
    public boolean shouldRouteToGray(String userId) {
        if (!settings.enabled()) {
            return false;
        }

        // 获取最新配置
        GrayConfig config = getConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }

        // 检查黑名单（永不进入灰度）
        if (config.getBlacklistUsers() != null && config.getBlacklistUsers().contains(userId)) {
            return false;
        }

        // 检查白名单（优先进入灰度）
        if (config.getWhitelistUsers() != null && config.getWhitelistUsers().contains(userId)) {
            return true;
        }

        // 检查用户是否已经被分配到灰度
        Boolean userGrayFlag = getUserGrayFlag(userId);
        if (userGrayFlag != null) {
            return userGrayFlag;
        }

        // 根据流量比例随机决定
        boolean shouldGray = ThreadLocalRandom.current().nextInt(100) < config.getTrafficRatio();
        
        // 缓存用户的灰度标记（保持一致性）
        setUserGrayFlag(userId, shouldGray);

        return shouldGray;
    }

    /**
     * 判断请求是否应该进入灰度（无用户上下文）
     * 使用请求特征（如 IP、设备ID）进行哈希
     *
     * @param requestKey 请求特征键
     * @return true 表示应该路由到灰度环境
     */
    public boolean shouldRouteToGrayByRequestKey(String requestKey) {
        if (!settings.enabled()) {
            return false;
        }

        GrayConfig config = getConfig();
        if (config == null || !config.isEnabled()) {
            return false;
        }

        // 使用哈希值决定是否进入灰度
        int hash = Math.abs(requestKey.hashCode());
        return (hash % 100) < config.getTrafficRatio();
    }

    /**
     * 获取灰度配置
     */
    public GrayConfig getConfig() {
        return store.getConfig();
    }

    /**
     * 更新灰度配置
     */
    public void updateConfig(GrayConfig config) {
        store.saveConfig(config);
        log.info("灰度配置已更新: trafficRatio={}%", config.getTrafficRatio());
    }

    /**
     * 获取用户的灰度标记
     */
    private Boolean getUserGrayFlag(String userId) {
        return store.getUserGrayFlag(userId);
    }

    /**
     * 设置用户的灰度标记
     */
    private void setUserGrayFlag(String userId, boolean isGray) {
        store.saveUserGrayFlag(userId, isGray);
    }

    /**
     * 清除用户的灰度标记
     */
    public void clearUserGrayFlag(String userId) {
        store.clearUserGrayFlag(userId);
    }

    /**
     * 清除所有用户的灰度标记
     */
    public void clearAllUserGrayFlags() {
        store.clearAllUserGrayFlags();
        log.info("已清除所有用户灰度标记");
    }
}
