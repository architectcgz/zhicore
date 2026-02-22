package com.zhicore.migration.infrastructure.gray;

import com.zhicore.migration.infrastructure.config.GrayReleaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 灰度发布配置
 * 管理灰度发布的全局状态和配置
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "gray.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GrayReleaseConfig {

    private final GrayReleaseProperties properties;
    private final RedissonClient redissonClient;

    private static final String GRAY_CONFIG_KEY = "gray:config";
    private static final String GRAY_STATUS_KEY = "gray:status";

    /**
     * 灰度路由器
     */
    @Bean
    public GrayRouter grayRouter() {
        return new GrayRouter(properties, redissonClient);
    }

    /**
     * 灰度数据对账任务
     */
    @Bean
    public GrayDataReconciliationTask grayDataReconciliationTask() {
        return new GrayDataReconciliationTask(redissonClient, properties);
    }

    /**
     * 灰度回滚服务
     */
    @Bean
    public GrayRollbackService grayRollbackService() {
        return new GrayRollbackService(redissonClient, properties);
    }

    /**
     * 初始化灰度配置到 Redis
     */
    @Bean
    public GrayConfigInitializer grayConfigInitializer() {
        return new GrayConfigInitializer(properties, redissonClient);
    }

    /**
     * 灰度配置初始化器
     */
    public static class GrayConfigInitializer {
        
        private final GrayReleaseProperties properties;
        private final RedissonClient redissonClient;

        public GrayConfigInitializer(GrayReleaseProperties properties, RedissonClient redissonClient) {
            this.properties = properties;
            this.redissonClient = redissonClient;
            initConfig();
        }

        private void initConfig() {
            // 存储灰度配置到 Redis
            RBucket<GrayConfig> configBucket = redissonClient.getBucket(GRAY_CONFIG_KEY);
            GrayConfig config = GrayConfig.builder()
                    .enabled(properties.isEnabled())
                    .trafficRatio(properties.getTrafficRatio())
                    .whitelistUsers(properties.getWhitelistUsers())
                    .blacklistUsers(properties.getBlacklistUsers())
                    .build();
            configBucket.set(config, Duration.ofDays(7));

            // 初始化灰度状态
            RBucket<GrayStatus> statusBucket = redissonClient.getBucket(GRAY_STATUS_KEY);
            if (!statusBucket.isExists()) {
                GrayStatus status = GrayStatus.builder()
                        .phase(GrayPhase.INITIAL)
                        .currentRatio(properties.getTrafficRatio())
                        .startTime(System.currentTimeMillis())
                        .build();
                statusBucket.set(status, Duration.ofDays(7));
            }

            log.info("灰度发布配置初始化完成: enabled={}, trafficRatio={}%", 
                    properties.isEnabled(), properties.getTrafficRatio());
        }
    }
}
