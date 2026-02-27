package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingArchiveProperties;
import com.zhicore.ranking.infrastructure.mongodb.RankingArchive;
import com.zhicore.ranking.infrastructure.mongodb.RankingArchiveRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 排行榜归档服务
 * <p>
 * 负责将 Redis 中的排行榜数据定期归档到 MongoDB，支持文章、创作者、话题三种实体类型。
 * 归档策略：
 * <ul>
 *   <li>日榜：每天凌晨 2:00 归档昨天的数据</li>
 *   <li>周榜：每周一凌晨 3:00 归档上周的数据</li>
 *   <li>月榜：每月 1 号凌晨 4:00 归档上月的数据</li>
 * </ul>
 * </p>
 * <p>
 * 实体类型说明：
 * <ul>
 *   <li>文章（post）：有独立的日榜、周榜、月榜数据</li>
 *   <li>创作者（creator）：使用总榜快照作为日榜、周榜、月榜数据</li>
 *   <li>话题（topic）：使用总榜快照作为日榜、周榜、月榜数据</li>
 * </ul>
 * </p>
 * <p>
 * 使用 @ConfigurationProperties 支持配置动态刷新
 * </p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
public class RankingArchiveService {

    private final RankingRedisRepository redisRepository;
    private final RankingArchiveRepository archiveRepository;
    private final RankingArchiveProperties archiveProperties;
    private final RedissonClient redissonClient;

    /** 分布式锁 key 前缀 */
    private static final String LOCK_PREFIX = "ranking:lock:archive:";

    public RankingArchiveService(RankingRedisRepository redisRepository,
                                  RankingArchiveRepository archiveRepository,
                                  RankingArchiveProperties archiveProperties,
                                  RedissonClient redissonClient) {
        this.redisRepository = redisRepository;
        this.archiveRepository = archiveRepository;
        this.archiveProperties = archiveProperties;
        this.redissonClient = redissonClient;
    }
    
    // ==================== 日榜归档 ====================
    
    /**
     * 归档日榜（每天凌晨 2:00 执行）
     * <p>
     * 归档文章、创作者、话题的日榜数据。
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void archiveDailyRanking() {
        executeWithLock("archive-daily", () -> {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info("开始归档日榜: date={}", yesterday);
            try {
                archiveDailyPosts(yesterday);
                archiveDailyCreators(yesterday);
                archiveDailyTopics(yesterday);
                log.info("日榜归档完成: date={}", yesterday);
            } catch (Exception e) {
                log.error("日榜归档失败: date={}", yesterday, e);
            }
        });
    }
    
    /**
     * 归档文章日榜
     * <p>
     * 从 Redis 获取指定日期的文章日榜 Top N 并保存到 MongoDB。
     * </p>
     *
     * @param date 日期
     */
    private void archiveDailyPosts(LocalDate date) {
        try {
            log.info("开始归档文章日榜: date={}", date);
            
            // 从 Redis 获取昨天的排行榜数据
            List<HotScore> scores = redisRepository.getDailyHotPostsWithScore(date, archiveProperties.getLimit());
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "post", "daily", date.getYear(), null, null, date);
            
            log.info("文章日榜归档完成: date={}, archived={}", date, archived);
        } catch (Exception e) {
            log.error("文章日榜归档失败: date={}", date, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档创作者日榜（总榜快照）
     * <p>
     * 从 Redis 获取创作者总榜 Top N 作为日榜快照并保存到 MongoDB。
     * </p>
     *
     * @param date 日期
     */
    private void archiveDailyCreators(LocalDate date) {
        try {
            log.info("开始归档创作者日榜: date={}", date);
            
            // 从 Redis 获取创作者总榜 Top N 作为日榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotCreators(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "creator", "daily", date.getYear(), null, null, date);
            
            log.info("创作者日榜归档完成: date={}, archived={}", date, archived);
        } catch (Exception e) {
            log.error("创作者日榜归档失败: date={}", date, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档话题日榜（总榜快照）
     * <p>
     * 从 Redis 获取话题总榜 Top N 作为日榜快照并保存到 MongoDB。
     * </p>
     *
     * @param date 日期
     */
    private void archiveDailyTopics(LocalDate date) {
        try {
            log.info("开始归档话题日榜: date={}", date);
            
            // 从 Redis 获取话题总榜 Top N 作为日榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotTopics(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "topic", "daily", date.getYear(), null, null, date);
            
            log.info("话题日榜归档完成: date={}, archived={}", date, archived);
        } catch (Exception e) {
            log.error("话题日榜归档失败: date={}", date, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    // ==================== 周榜归档 ====================
    
    /**
     * 归档周榜（每周一凌晨 3:00 执行）
     * <p>
     * 归档文章、创作者、话题的周榜数据。
     * 使用 LocalDate.now().minusWeeks(1) 计算上周的年份和周数，正确处理跨年情况。
     * </p>
     */
    @Scheduled(cron = "0 0 3 ? * MON")
    public void archiveWeeklyRanking() {
        executeWithLock("archive-weekly", () -> {
            LocalDate lastWeek = LocalDate.now().minusWeeks(1);
            int year = lastWeek.getYear();
            int weekNumber = getWeekNumber(lastWeek);
            log.info("开始归档周榜: year={}, week={}", year, weekNumber);
            try {
                archiveWeeklyPosts(year, weekNumber);
                archiveWeeklyCreators(year, weekNumber);
                archiveWeeklyTopics(year, weekNumber);
                log.info("周榜归档完成: year={}, week={}", year, weekNumber);
            } catch (Exception e) {
                log.error("周榜归档失败: year={}, week={}", year, weekNumber, e);
            }
        });
    }
    
    /**
     * 归档文章周榜
     * <p>
     * 从 Redis 获取指定周的文章周榜 Top N 并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param weekNumber 周数
     */
    private void archiveWeeklyPosts(int year, int weekNumber) {
        try {
            log.info("开始归档文章周榜: year={}, week={}", year, weekNumber);
            
            // 从 Redis 获取上周的排行榜数据
            List<HotScore> scores = redisRepository.getWeeklyHotPostsWithScore(weekNumber, archiveProperties.getLimit());
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "post", "weekly", year, null, weekNumber, null);
            
            log.info("文章周榜归档完成: year={}, week={}, archived={}", year, weekNumber, archived);
        } catch (Exception e) {
            log.error("文章周榜归档失败: year={}, week={}", year, weekNumber, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档创作者周榜（总榜快照）
     * <p>
     * 从 Redis 获取创作者总榜 Top N 作为周榜快照并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param weekNumber 周数
     */
    private void archiveWeeklyCreators(int year, int weekNumber) {
        try {
            log.info("开始归档创作者周榜: year={}, week={}", year, weekNumber);
            
            // 从 Redis 获取创作者总榜 Top N 作为周榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotCreators(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "creator", "weekly", year, null, weekNumber, null);
            
            log.info("创作者周榜归档完成: year={}, week={}, archived={}", year, weekNumber, archived);
        } catch (Exception e) {
            log.error("创作者周榜归档失败: year={}, week={}", year, weekNumber, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档话题周榜（总榜快照）
     * <p>
     * 从 Redis 获取话题总榜 Top N 作为周榜快照并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param weekNumber 周数
     */
    private void archiveWeeklyTopics(int year, int weekNumber) {
        try {
            log.info("开始归档话题周榜: year={}, week={}", year, weekNumber);
            
            // 从 Redis 获取话题总榜 Top N 作为周榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotTopics(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "topic", "weekly", year, null, weekNumber, null);
            
            log.info("话题周榜归档完成: year={}, week={}, archived={}", year, weekNumber, archived);
        } catch (Exception e) {
            log.error("话题周榜归档失败: year={}, week={}", year, weekNumber, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    // ==================== 月榜归档 ====================
    
    /**
     * 归档月榜（每月 1 号凌晨 4:00 执行）
     * <p>
     * 归档文章、创作者、话题的月榜数据。
     * </p>
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    public void archiveMonthlyRanking() {
        executeWithLock("archive-monthly", () -> {
            LocalDate lastMonth = LocalDate.now().minusMonths(1);
            int year = lastMonth.getYear();
            int month = lastMonth.getMonthValue();
            log.info("开始归档月榜: year={}, month={}", year, month);
            try {
                archiveMonthlyPosts(year, month);
                archiveMonthlyCreators(year, month);
                archiveMonthlyTopics(year, month);
                log.info("月榜归档完成: year={}, month={}", year, month);
            } catch (Exception e) {
                log.error("月榜归档失败: year={}, month={}", year, month, e);
            }
        });
    }
    
    /**
     * 归档文章月榜
     * <p>
     * 从 Redis 获取指定月份的文章月榜 Top N 并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param month 月份（1-12）
     */
    private void archiveMonthlyPosts(int year, int month) {
        try {
            log.info("开始归档文章月榜: year={}, month={}", year, month);
            
            // 从 Redis 获取上月的排行榜数据
            List<HotScore> scores = redisRepository.getMonthlyHotPostsWithScore(year, month, archiveProperties.getLimit());
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "post", "monthly", year, month, null, null);
            
            log.info("文章月榜归档完成: year={}, month={}, archived={}", year, month, archived);
        } catch (Exception e) {
            log.error("文章月榜归档失败: year={}, month={}", year, month, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档创作者月榜（总榜快照）
     * <p>
     * 从 Redis 获取创作者总榜 Top N 作为月榜快照并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param month 月份（1-12）
     */
    private void archiveMonthlyCreators(int year, int month) {
        try {
            log.info("开始归档创作者月榜: year={}, month={}", year, month);
            
            // 从 Redis 获取创作者总榜 Top N 作为月榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotCreators(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "creator", "monthly", year, month, null, null);
            
            log.info("创作者月榜归档完成: year={}, month={}, archived={}", year, month, archived);
        } catch (Exception e) {
            log.error("创作者月榜归档失败: year={}, month={}", year, month, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    /**
     * 归档话题月榜（总榜快照）
     * <p>
     * 从 Redis 获取话题总榜 Top N 作为月榜快照并保存到 MongoDB。
     * </p>
     *
     * @param year 年份
     * @param month 月份（1-12）
     */
    private void archiveMonthlyTopics(int year, int month) {
        try {
            log.info("开始归档话题月榜: year={}, month={}", year, month);
            
            // 从 Redis 获取话题总榜 Top N 作为月榜快照
            List<HotScore> scores = redisRepository.getTopRanking(
                RankingRedisKeys.hotTopics(), 0, archiveProperties.getLimit() - 1);
            
            // 归档到 MongoDB
            int archived = archiveRankingData(scores, "topic", "monthly", year, month, null, null);
            
            log.info("话题月榜归档完成: year={}, month={}, archived={}", year, month, archived);
        } catch (Exception e) {
            log.error("话题月榜归档失败: year={}, month={}", year, month, e);
            // 单个实体类型失败不影响其他类型
        }
    }
    
    // ==================== 分布式锁 ====================

    /**
     * 使用分布式锁执行归档任务，确保多实例部署时只有一个实例执行
     *
     * @param taskName 任务名称（用于锁 key 和日志）
     * @param task     要执行的任务
     */
    private void executeWithLock(String taskName, Runnable task) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + taskName);
        try {
            // tryLock(0, ...) 非阻塞：拿不到锁立即跳过，避免多实例重复执行
            if (lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                try {
                    log.info("获取分布式锁成功，开始执行归档任务: {}", taskName);
                    task.run();
                } finally {
                    lock.unlock();
                }
            } else {
                log.debug("归档任务已被其他实例执行，跳过: {}", taskName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取归档分布式锁被中断: {}", taskName);
        }
    }

    // ==================== 通用方法 ====================
    
    /**
     * 通用归档方法
     * <p>
     * 统一处理所有类型的归档逻辑（文章、创作者、话题），防止重复归档。
     * </p>
     *
     * @param scores 热度分数列表
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param year 年份
     * @param month 月份（可选，月榜使用）
     * @param week 周数（可选，周榜使用）
     * @param date 日期（可选，日榜使用）
     * @return 归档数量
     */
    private int archiveRankingData(List<HotScore> scores, String entityType, String rankingType,
                                   Integer year, Integer month, Integer week, LocalDate date) {
        int archived = 0;
        
        for (HotScore score : scores) {
            RankingArchive archive = RankingArchive.builder()
                .entityId(score.getEntityId())
                .entityType(entityType)
                .score(score.getScore())
                .rank(score.getRank())
                .rankingType(rankingType)
                .period(RankingArchive.PeriodInfo.builder()
                    .year(year)
                    .month(month)
                    .week(week)
                    .date(date)
                    .build())
                .metadata(new HashMap<>())
                .archivedAt(LocalDateTime.now())
                .version(1)
                .build();
            
            // 检查是否已归档（防止重复）
            // 注意：null 参数会匹配 MongoDB 中字段不存在或值为 null 的文档。
            // 这是预期行为，因为不同排行榜类型使用不同的时间字段：
            // - 日榜：year 和 date 有值，month 和 week 为 null
            // - 周榜：year 和 week 有值，month 和 date 为 null
            // - 月榜：year 和 month 有值，week 和 date 为 null
            if (!archiveRepository.existsByEntityIdAndEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthAndPeriod_WeekAndPeriod_Date(
                archive.getEntityId(),
                archive.getEntityType(),
                archive.getRankingType(),
                archive.getPeriod().getYear(),
                archive.getPeriod().getMonth(),
                archive.getPeriod().getWeek(),
                archive.getPeriod().getDate()
            )) {
                archiveRepository.save(archive);
                archived++;
            }
        }
        
        return archived;
    }
    
    /**
     * 查询历史排行榜（从 MongoDB）
     * <p>
     * 支持查询不同实体类型的历史数据。
     * </p>
     *
     * @param entityType 实体类型（post、creator、topic）
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 排行榜归档列表
     */
    public List<RankingArchive> getMonthlyArchive(String entityType, int year, int month, int limit) {
        List<RankingArchive> archives = archiveRepository
            .findByEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthOrderByRankAsc(
                entityType, "monthly", year, month
            );
        
        return archives.stream()
            .limit(limit)
            .toList();
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取指定日期的周数
     * <p>
     * 使用系统默认的 Locale 计算周数，确保与 RankingRedisKeys 中的计算方式一致。
     * </p>
     *
     * @param date 日期
     * @return 周数
     */
    private int getWeekNumber(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfWeekBasedYear());
    }
}
