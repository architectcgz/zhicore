package com.blog.migration.infrastructure.gray;

import com.blog.migration.infrastructure.config.GrayReleaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度路由决策器
 * 决定请求是否应该路由到灰度环境
 */
@Slf4j
@RequiredArgsConstructor
public class GrayRouter {

    private final GrayReleaseProperties properties;
    private final RedissonClient redissonClient;

    private static final String GRAY_CONFIG_KEY = "gray:config";
    private static final String USER_GRAY_FLAG_PREFIX = "gray:user:";

    /**
     * 判断用户是否应该进入灰度
     *
     * @param userId 用户ID
     * @return true 表示应该路由到灰度环境
     */
    public boolean shouldRouteToGray(String userId) {
        if (!properties.isEnabled()) {
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
        if (!properties.isEnabled()) {
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
        RBucket<GrayConfig> bucket = redissonClient.getBucket(GRAY_CONFIG_KEY);
        return bucket.get();
    }

    /**
     * 更新灰度配置
     */
    public void updateConfig(GrayConfig config) {
        RBucket<GrayConfig> bucket = redissonClient.getBucket(GRAY_CONFIG_KEY);
        bucket.set(config);
        log.info("灰度配置已更新: trafficRatio={}%", config.getTrafficRatio());
    }

    /**
     * 获取用户的灰度标记
     */
    private Boolean getUserGrayFlag(String userId) {
        String key = USER_GRAY_FLAG_PREFIX + userId;
        RBucket<Boolean> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    /**
     * 设置用户的灰度标记
     */
    private void setUserGrayFlag(String userId, boolean isGray) {
        String key = USER_GRAY_FLAG_PREFIX + userId;
        RBucket<Boolean> bucket = redissonClient.getBucket(key);
        bucket.set(isGray);
    }

    /**
     * 清除用户的灰度标记
     */
    public void clearUserGrayFlag(String userId) {
        String key = USER_GRAY_FLAG_PREFIX + userId;
        redissonClient.getBucket(key).delete();
    }

    /**
     * 清除所有用户的灰度标记
     */
    public void clearAllUserGrayFlags() {
        redissonClient.getKeys().deleteByPattern(USER_GRAY_FLAG_PREFIX + "*");
        log.info("已清除所有用户灰度标记");
    }
}
