package com.blog.ranking.infrastructure.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 排行榜归档文档
 * <p>
 * 用于永久保存历史排行榜数据到 MongoDB，支持多种实体类型（文章、创作者、话题）
 * 和多种排行榜类型（日榜、周榜、月榜）。
 * </p>
 *
 * @author Blog Team
 */
@Document(collection = "ranking_archive")
@CompoundIndexes({
    @CompoundIndex(
        name = "idx_type_period_rank",
        def = "{'entityType': 1, 'rankingType': 1, 'period.year': -1, 'period.month': -1, 'rank': 1}"
    ),
    @CompoundIndex(
        name = "idx_entity_history",
        def = "{'entityId': 1, 'entityType': 1, 'rankingType': 1, 'period.year': -1}"
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingArchive {
    
    /**
     * 文档ID（MongoDB 自动生成）
     */
    @Id
    private String id;
    
    /**
     * 实体ID（文章ID、用户ID、话题ID）
     */
    @Indexed
    private String entityId;
    
    /**
     * 实体类型：post（文章）、creator（创作者）、topic（话题）
     */
    @Indexed
    private String entityType;
    
    /**
     * 热度分数
     */
    private Double score;
    
    /**
     * 排名（从1开始）
     */
    @Indexed
    private Integer rank;
    
    /**
     * 排行榜类型：daily（日榜）、weekly（周榜）、monthly（月榜）
     */
    @Indexed
    private String rankingType;
    
    /**
     * 时间周期信息
     */
    @Indexed
    private PeriodInfo period;
    
    /**
     * 扩展字段（灵活存储额外信息）
     * <p>
     * 例如：文章标题、用户名、浏览量等
     * </p>
     */
    private Map<String, Object> metadata;
    
    /**
     * 归档时间
     */
    @Indexed
    private LocalDateTime archivedAt;
    
    /**
     * 数据版本（用于兼容性）
     */
    private Integer version;
    
    /**
     * 时间周期信息
     * <p>
     * 根据排行榜类型使用不同的字段：
     * <ul>
     *   <li>日榜：使用 year 和 date</li>
     *   <li>周榜：使用 year 和 week</li>
     *   <li>月榜：使用 year 和 month</li>
     * </ul>
     * </p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodInfo {
        /**
         * 年份（必需）
         */
        private Integer year;
        
        /**
         * 月份（1-12，月榜使用）
         */
        private Integer month;
        
        /**
         * 周数（周榜使用）
         */
        private Integer week;
        
        /**
         * 日期（日榜使用）
         */
        private LocalDate date;
    }
}
