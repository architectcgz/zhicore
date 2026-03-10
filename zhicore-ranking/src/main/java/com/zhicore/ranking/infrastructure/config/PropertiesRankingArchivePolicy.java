package com.zhicore.ranking.infrastructure.config;

import com.zhicore.ranking.application.port.policy.RankingArchivePolicy;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于配置属性的排行榜归档策略实现。
 */
@Component
@RequiredArgsConstructor
public class PropertiesRankingArchivePolicy implements RankingArchivePolicy {

    private final RankingArchiveProperties archiveProperties;

    @Override
    public int archiveLimit() {
        return archiveProperties.getLimit();
    }

    @Override
    public String dailyArchiveLockKey() {
        return RankingRedisKeys.archiveLock("archive-daily");
    }

    @Override
    public String weeklyArchiveLockKey() {
        return RankingRedisKeys.archiveLock("archive-weekly");
    }

    @Override
    public String monthlyArchiveLockKey() {
        return RankingRedisKeys.archiveLock("archive-monthly");
    }
}
