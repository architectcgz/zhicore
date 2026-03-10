package com.zhicore.ranking.application.port.policy;

/**
 * 排行快照刷新策略端口。
 */
public interface RankingSnapshotPolicy {

    int topSize();
}
