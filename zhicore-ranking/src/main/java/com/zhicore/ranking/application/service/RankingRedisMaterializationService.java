package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingPostStateRecord;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 将 flush 后的 ranking 状态增量物化到 Redis。
 *
 * <p>总榜使用最新 hot score 覆盖，周期榜使用 flush 产生的分数增量更新。</p>
 */
@Service
@RequiredArgsConstructor
public class RankingRedisMaterializationService {

    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration WEEKLY_TTL = Duration.ofDays(14);
    private static final Duration MONTHLY_TTL = Duration.ofDays(365);

    private final RankingRedisRepository rankingRedisRepository;

    public void applyFlush(RankingBucketRecord bucket,
                           RankingPostStateRecord previousState,
                           RankingPostStateRecord currentState,
                           double periodScoreDelta) {
        LocalDate bucketDate = bucket.getBucketStart().toLocalDate();
        String postId = String.valueOf(currentState.getPostId());
        upsertTotalPost(postId, currentState.getHotScore());

        double totalScoreDelta = currentState.getHotScore() - (previousState != null ? previousState.getHotScore() : 0D);
        if (totalScoreDelta != 0D) {
            applyTotalCreatorDelta(currentState.getAuthorId(), totalScoreDelta);
            applyTotalTopicDelta(currentState.getTopicIds(), totalScoreDelta);
        }
        if (periodScoreDelta != 0D) {
            applyPeriodPostDelta(bucketDate, postId, periodScoreDelta);
            applyPeriodCreatorDelta(bucketDate, currentState.getAuthorId(), periodScoreDelta);
            applyPeriodTopicDelta(bucketDate, currentState.getTopicIds(), periodScoreDelta);
        }
    }

    private void upsertTotalPost(String postId, double hotScore) {
        if (hotScore > 0D) {
            rankingRedisRepository.updatePostScore(postId, hotScore);
            return;
        }
        rankingRedisRepository.removeMember(RankingRedisKeys.hotPosts(), postId);
    }

    private void applyTotalCreatorDelta(Long authorId, double delta) {
        if (authorId == null) {
            return;
        }
        String member = String.valueOf(authorId);
        Double score = rankingRedisRepository.incrementScore(RankingRedisKeys.hotCreators(), member, delta);
        if (score != null && score <= 0D) {
            rankingRedisRepository.removeMember(RankingRedisKeys.hotCreators(), member);
        }
    }

    private void applyTotalTopicDelta(List<Long> topicIds, double delta) {
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        for (Long topicId : topicIds) {
            if (topicId == null) {
                continue;
            }
            String member = String.valueOf(topicId);
            Double score = rankingRedisRepository.incrementScore(RankingRedisKeys.hotTopics(), member, delta);
            if (score != null && score <= 0D) {
                rankingRedisRepository.removeMember(RankingRedisKeys.hotTopics(), member);
            }
        }
    }

    private void applyPeriodPostDelta(LocalDate bucketDate, String postId, double delta) {
        incrementPeriodKey(RankingRedisKeys.dailyPosts(bucketDate), postId, delta, DAILY_TTL);

        int weekBasedYear = RankingRedisKeys.getWeekBasedYear(bucketDate);
        int weekNumber = RankingRedisKeys.getWeekNumber(bucketDate);
        incrementPeriodKey(RankingRedisKeys.weeklyPosts(weekBasedYear, weekNumber), postId, delta, WEEKLY_TTL);

        incrementPeriodKey(RankingRedisKeys.monthlyPosts(bucketDate.getYear(), bucketDate.getMonthValue()), postId, delta, MONTHLY_TTL);
    }

    private void applyPeriodCreatorDelta(LocalDate bucketDate, Long authorId, double delta) {
        if (authorId == null) {
            return;
        }
        String member = String.valueOf(authorId);
        incrementPeriodKey(RankingRedisKeys.dailyCreators(bucketDate), member, delta, DAILY_TTL);

        int weekBasedYear = RankingRedisKeys.getWeekBasedYear(bucketDate);
        int weekNumber = RankingRedisKeys.getWeekNumber(bucketDate);
        incrementPeriodKey(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), member, delta, WEEKLY_TTL);

        incrementPeriodKey(RankingRedisKeys.monthlyCreators(bucketDate.getYear(), bucketDate.getMonthValue()), member, delta, MONTHLY_TTL);
    }

    private void applyPeriodTopicDelta(LocalDate bucketDate, List<Long> topicIds, double delta) {
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        int weekBasedYear = RankingRedisKeys.getWeekBasedYear(bucketDate);
        int weekNumber = RankingRedisKeys.getWeekNumber(bucketDate);
        int year = bucketDate.getYear();
        int month = bucketDate.getMonthValue();
        for (Long topicId : topicIds) {
            if (topicId == null) {
                continue;
            }
            String member = String.valueOf(topicId);
            incrementPeriodKey(RankingRedisKeys.dailyTopics(bucketDate), member, delta, DAILY_TTL);
            incrementPeriodKey(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), member, delta, WEEKLY_TTL);
            incrementPeriodKey(RankingRedisKeys.monthlyTopics(year, month), member, delta, MONTHLY_TTL);
        }
    }

    private void incrementPeriodKey(String key, String member, double delta, Duration ttl) {
        Double score = rankingRedisRepository.incrementScore(key, member, delta);
        rankingRedisRepository.setExpire(key, ttl);
        if (score != null && score <= 0D) {
            rankingRedisRepository.removeMember(key, member);
        }
    }
}
