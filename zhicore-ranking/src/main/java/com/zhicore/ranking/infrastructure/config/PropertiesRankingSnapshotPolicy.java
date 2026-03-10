package com.zhicore.ranking.infrastructure.config;

import com.zhicore.ranking.application.port.policy.RankingSnapshotPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于配置属性的排行快照刷新策略实现。
 */
@Component
@RequiredArgsConstructor
public class PropertiesRankingSnapshotPolicy implements RankingSnapshotPolicy {

    private final RankingSnapshotProperties snapshotProperties;

    @Override
    public int topSize() {
        return snapshotProperties.getTopSize();
    }
}
