package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.gray.GrayConfig;
import com.zhicore.migration.service.gray.GrayDataReconciliationTask;
import com.zhicore.migration.service.gray.GrayRollbackService;
import com.zhicore.migration.service.gray.GrayStatus;
import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 基于 Redis 的灰度状态存储实现。
 */
@RequiredArgsConstructor
public class RedisGrayReleaseStore implements GrayReleaseStore {

    private static final Duration GRAY_STATE_TTL = Duration.ofDays(7);
    private static final String GRAY_CONFIG_KEY = "gray:config";
    private static final String GRAY_STATUS_KEY = "gray:status";
    private static final String USER_GRAY_FLAG_PREFIX = "gray:user:";
    private static final String RECONCILIATION_RESULT_KEY = "gray:reconciliation:result";
    private static final String RECONCILIATION_HISTORY_KEY = "gray:reconciliation:history";
    private static final String ROLLBACK_HISTORY_KEY = "gray:rollback:history";

    private final RedissonClient redissonClient;

    @Override
    public GrayConfig getConfig() {
        return redissonClient.<GrayConfig>getBucket(GRAY_CONFIG_KEY).get();
    }

    @Override
    public void saveConfig(GrayConfig config) {
        redissonClient.<GrayConfig>getBucket(GRAY_CONFIG_KEY).set(config);
    }

    @Override
    public void initializeConfig(GrayConfig config) {
        redissonClient.<GrayConfig>getBucket(GRAY_CONFIG_KEY).set(config, GRAY_STATE_TTL);
    }

    @Override
    public GrayStatus getStatus() {
        return redissonClient.<GrayStatus>getBucket(GRAY_STATUS_KEY).get();
    }

    @Override
    public void saveStatus(GrayStatus status) {
        redissonClient.<GrayStatus>getBucket(GRAY_STATUS_KEY).set(status, GRAY_STATE_TTL);
    }

    @Override
    public void initializeStatusIfAbsent(GrayStatus status) {
        RBucket<GrayStatus> bucket = redissonClient.getBucket(GRAY_STATUS_KEY);
        if (!bucket.isExists()) {
            bucket.set(status, GRAY_STATE_TTL);
        }
    }

    @Override
    public Boolean getUserGrayFlag(String userId) {
        return redissonClient.<Boolean>getBucket(userGrayFlagKey(userId)).get();
    }

    @Override
    public void saveUserGrayFlag(String userId, boolean isGray) {
        redissonClient.<Boolean>getBucket(userGrayFlagKey(userId)).set(isGray);
    }

    @Override
    public void clearUserGrayFlag(String userId) {
        redissonClient.getBucket(userGrayFlagKey(userId)).delete();
    }

    @Override
    public void clearAllUserGrayFlags() {
        redissonClient.getKeys().deleteByPattern(USER_GRAY_FLAG_PREFIX + "*");
    }

    @Override
    public void saveReconciliationResult(GrayDataReconciliationTask.ReconciliationResult result) {
        redissonClient.<GrayDataReconciliationTask.ReconciliationResult>getBucket(RECONCILIATION_RESULT_KEY)
                .set(result);
        redissonClient.getList(RECONCILIATION_HISTORY_KEY).add(result);
    }

    @Override
    public GrayDataReconciliationTask.ReconciliationResult getLatestReconciliationResult() {
        return redissonClient.<GrayDataReconciliationTask.ReconciliationResult>getBucket(RECONCILIATION_RESULT_KEY)
                .get();
    }

    @Override
    public void saveRollbackHistory(GrayRollbackService.RollbackResult result) {
        redissonClient.getList(ROLLBACK_HISTORY_KEY).add(result);
    }

    private String userGrayFlagKey(String userId) {
        return USER_GRAY_FLAG_PREFIX + userId;
    }
}
