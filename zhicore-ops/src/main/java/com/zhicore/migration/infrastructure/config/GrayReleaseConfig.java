package com.zhicore.migration.infrastructure.config;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.migration.infrastructure.store.RedisGrayReleaseStore;
import com.zhicore.migration.service.gray.GrayConfig;
import com.zhicore.migration.service.gray.GrayDataReconciliationTask;
import com.zhicore.migration.service.gray.GrayPhase;
import com.zhicore.migration.service.gray.GrayReleaseSettings;
import com.zhicore.migration.service.gray.GrayRollbackService;
import com.zhicore.migration.service.gray.GrayRouter;
import com.zhicore.migration.service.gray.GrayStatus;
import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 灰度发布配置。
 * 管理灰度发布的全局状态和配置。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "gray.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GrayReleaseConfig {

    private final GrayReleaseProperties properties;
    private final RedissonClient redissonClient;

    /** 灰度状态存储。 */
    @Bean
    public GrayReleaseStore grayReleaseStore() {
        return new RedisGrayReleaseStore(redissonClient);
    }

    /** 灰度路由器。 */
    @Bean
    public GrayRouter grayRouter(GrayReleaseStore grayReleaseStore) {
        return new GrayRouter(toSettings(), grayReleaseStore);
    }

    /** 灰度数据对账任务。 */
    @Bean
    public GrayDataReconciliationTask grayDataReconciliationTask(GrayReleaseStore grayReleaseStore,
                                                                 DistributedLockExecutor distributedLockExecutor) {
        return new GrayDataReconciliationTask(grayReleaseStore, toSettings(), distributedLockExecutor);
    }

    /** 灰度回滚服务。 */
    @Bean
    public GrayRollbackService grayRollbackService(GrayReleaseStore grayReleaseStore) {
        return new GrayRollbackService(grayReleaseStore, toSettings());
    }

    /** 初始化灰度配置到 Redis。 */
    @Bean
    public GrayConfigInitializer grayConfigInitializer(GrayReleaseStore grayReleaseStore) {
        return new GrayConfigInitializer(properties, grayReleaseStore);
    }

    private GrayReleaseSettings toSettings() {
        return new GrayReleaseSettings(
                properties.isEnabled(),
                properties.getTrafficRatio(),
                Set.copyOf(properties.getWhitelistUsers()),
                Set.copyOf(properties.getBlacklistUsers()),
                new GrayReleaseSettings.AlertSettings(
                        properties.getAlert().getErrorRateThreshold(),
                        properties.getAlert().getLatencyThresholdMs()
                )
        );
    }

    /** 灰度配置初始化器。 */
    public static class GrayConfigInitializer {

        private final GrayReleaseProperties properties;
        private final GrayReleaseStore grayReleaseStore;

        public GrayConfigInitializer(GrayReleaseProperties properties, GrayReleaseStore grayReleaseStore) {
            this.properties = properties;
            this.grayReleaseStore = grayReleaseStore;
            initConfig();
        }

        private void initConfig() {
            GrayConfig config = GrayConfig.builder()
                    .enabled(properties.isEnabled())
                    .trafficRatio(properties.getTrafficRatio())
                    .whitelistUsers(properties.getWhitelistUsers())
                    .blacklistUsers(properties.getBlacklistUsers())
                    .build();
            grayReleaseStore.initializeConfig(config);

            GrayStatus status = GrayStatus.builder()
                    .phase(GrayPhase.INITIAL)
                    .currentRatio(properties.getTrafficRatio())
                    .startTime(System.currentTimeMillis())
                    .build();
            grayReleaseStore.initializeStatusIfAbsent(status);

            log.info("灰度发布配置初始化完成: enabled={}, trafficRatio={}%",
                    properties.isEnabled(), properties.getTrafficRatio());
        }
    }
}
