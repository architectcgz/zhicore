package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.application.model.RankingInboxEventRecord;

/**
 * Ranking inbox 写入存储端口。
 */
public interface RankingEventInboxStore {

    boolean saveNewEvent(RankingInboxEventRecord event);
}
