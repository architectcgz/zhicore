package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.application.model.AggregationInboxEvent;
import com.zhicore.ranking.application.model.AggregationPostHotState;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ranking inbox 聚合存储端口。
 *
 * 封装 claim、状态读写、完成/失败回写和过期清理等基础设施工作流。
 */
public interface RankingInboxAggregationStore {

    List<AggregationInboxEvent> claimPendingEvents();

    Map<Long, AggregationPostHotState> findStatesByPostIds(Collection<Long> postIds);

    void saveState(AggregationPostHotState state);

    void markDone(Collection<AggregationInboxEvent> events);

    void markFailed(Collection<AggregationInboxEvent> events, String errorMessage);

    void cleanupExpiredDoneEvents();

    int recentAppliedEventWindowSize();
}
