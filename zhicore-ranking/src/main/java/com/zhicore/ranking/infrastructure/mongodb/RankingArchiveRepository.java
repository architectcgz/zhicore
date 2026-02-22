package com.zhicore.ranking.infrastructure.mongodb;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 排行榜归档仓储
 * <p>
 * 提供对 MongoDB 中历史排行榜数据的查询和管理功能。
 * 支持按时间周期（日、周、月）和实体类型查询排行榜数据。
 * </p>
 *
 * @author ZhiCore Team
 */
@Repository
public interface RankingArchiveRepository extends MongoRepository<RankingArchive, String> {
    
    /**
     * 查询指定月份的排行榜
     * <p>
     * 按排名升序返回结果。
     * </p>
     *
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param year 年份
     * @param month 月份（1-12）
     * @return 排行榜归档列表，按排名升序排列
     */
    List<RankingArchive> findByEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthOrderByRankAsc(
        String entityType,
        String rankingType,
        Integer year,
        Integer month
    );
    
    /**
     * 查询指定周的排行榜
     * <p>
     * 按排名升序返回结果。
     * </p>
     *
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param year 年份
     * @param week 周数
     * @return 排行榜归档列表，按排名升序排列
     */
    List<RankingArchive> findByEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_WeekOrderByRankAsc(
        String entityType,
        String rankingType,
        Integer year,
        Integer week
    );
    
    /**
     * 查询指定日期的排行榜
     * <p>
     * 按排名升序返回结果。
     * </p>
     *
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param date 日期
     * @return 排行榜归档列表，按排名升序排列
     */
    List<RankingArchive> findByEntityTypeAndRankingTypeAndPeriod_DateOrderByRankAsc(
        String entityType,
        String rankingType,
        LocalDate date
    );
    
    /**
     * 查询某实体的历史排名
     * <p>
     * 支持分页查询，按年份降序排列。
     * 可用于查看某文章、创作者或话题在历史上的排名变化趋势。
     * </p>
     *
     * @param entityId 实体ID
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param pageable 分页参数
     * @return 排行榜归档分页结果
     */
    @Query("{ 'entityId': ?0, 'entityType': ?1, 'rankingType': ?2 }")
    Page<RankingArchive> findEntityRankingHistory(
        String entityId,
        String entityType,
        String rankingType,
        Pageable pageable
    );
    
    /**
     * 检查是否已归档
     * <p>
     * 用于防止重复归档同一时间段的数据。
     * </p>
     * <p>
     * <strong>注意</strong>：null 参数会匹配 MongoDB 中字段不存在或值为 null 的文档。
     * 这是预期行为，因为不同排行榜类型使用不同的时间字段：
     * <ul>
     *   <li>日榜：year 和 date 有值，month 和 week 为 null</li>
     *   <li>周榜：year 和 week 有值，month 和 date 为 null</li>
     *   <li>月榜：year 和 month 有值，week 和 date 为 null</li>
     * </ul>
     * </p>
     *
     * @param entityId 实体ID
     * @param entityType 实体类型（post、creator、topic）
     * @param rankingType 排行榜类型（daily、weekly、monthly）
     * @param year 年份
     * @param month 月份（可为 null）
     * @param week 周数（可为 null）
     * @param date 日期（可为 null）
     * @return 如果已存在则返回 true，否则返回 false
     */
    boolean existsByEntityIdAndEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthAndPeriod_WeekAndPeriod_Date(
        String entityId,
        String entityType,
        String rankingType,
        Integer year,
        Integer month,
        Integer week,
        LocalDate date
    );
}
