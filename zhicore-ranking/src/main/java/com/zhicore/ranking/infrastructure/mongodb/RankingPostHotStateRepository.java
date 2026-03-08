package com.zhicore.ranking.infrastructure.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章热度权威状态仓储。
 */
@Repository
public interface RankingPostHotStateRepository extends MongoRepository<RankingPostHotState, Long> {

    List<RankingPostHotState> findByStatus(String status);
}
