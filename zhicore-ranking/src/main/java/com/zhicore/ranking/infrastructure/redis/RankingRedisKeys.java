package com.zhicore.ranking.infrastructure.redis;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.util.IsoWeekUtils;

import java.time.LocalDate;

/**
 * 排行榜 Redis Key 常量
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 排行榜特殊情况：ranking:{type}:{dimension}:{period}
 * 示例：ranking:posts:hot, ranking:posts:daily:2025-01-15
 *
 * @author ZhiCore Team
 */
public final class RankingRedisKeys {

    private static String prefix() {
        return CacheConstants.withNamespace("ranking");
    }

    private RankingRedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 文章排行榜 ====================

    /**
     * 热门文章总榜
     * Key: ranking:posts:hot
     * 
     * @return Redis key
     */
    public static String hotPosts() {
        return prefix() + ":posts:hot";
    }

    /**
     * 热门文章日榜
     * Key: ranking:posts:daily:{date}
     * 
     * @param date 日期
     * @return Redis key
     */
    public static String dailyPosts(LocalDate date) {
        return prefix() + ":posts:daily:" + date.toString();
    }

    /**
     * 今日文章日榜
     * Key: ranking:posts:daily:{today}
     * 
     * @return Redis key
     */
    public static String todayPosts() {
        return dailyPosts(LocalDate.now());
    }

    /**
     * 热门文章周榜
     * Key: ranking:posts:weekly:{weekBasedYear}:{weekNumber}
     * 
     * @param weekBasedYear 周所属 ISO week-based year
     * @param weekNumber 周数
     * @return Redis key
     */
    public static String weeklyPosts(int weekBasedYear, int weekNumber) {
        return prefix() + ":posts:weekly:" + weekBasedYear + ":" + String.format("%02d", weekNumber);
    }

    /**
     * 本周文章周榜
     * Key: ranking:posts:weekly:{currentWeek}
     * 
     * @return Redis key
     */
    public static String currentWeekPosts() {
        LocalDate now = LocalDate.now();
        return weeklyPosts(getWeekBasedYear(now), getWeekNumber(now));
    }

    /**
     * 热门文章月榜
     * Key: ranking:posts:monthly:{year}:{month}
     * 
     * @param year 年份
     * @param month 月份（1-12）
     * @return Redis key
     */
    public static String monthlyPosts(int year, int month) {
        return prefix() + ":posts:monthly:" + year + ":" + String.format("%02d", month);
    }

    /**
     * 本月文章月榜
     * Key: ranking:posts:monthly:{currentYear}:{currentMonth}
     * 
     * @return Redis key
     */
    public static String currentMonthPosts() {
        LocalDate now = LocalDate.now();
        return monthlyPosts(now.getYear(), now.getMonthValue());
    }

    // ==================== 创作者排行榜 ====================

    /**
     * 创作者热度总榜
     * Key: ranking:creators:hot
     * 
     * @return Redis key
     */
    public static String hotCreators() {
        return prefix() + ":creators:hot";
    }

    /**
     * 创作者日榜
     * Key: ranking:creators:daily:{date}
     * 
     * @param date 日期
     * @return Redis key
     */
    public static String dailyCreators(LocalDate date) {
        return prefix() + ":creators:daily:" + date.toString();
    }

    /**
     * 创作者周榜。
     */
    public static String weeklyCreators(int weekBasedYear, int weekNumber) {
        return prefix() + ":creators:weekly:" + weekBasedYear + ":" + String.format("%02d", weekNumber);
    }

    /**
     * 创作者月榜。
     */
    public static String monthlyCreators(int year, int month) {
        return prefix() + ":creators:monthly:" + year + ":" + String.format("%02d", month);
    }

    // ==================== 话题排行榜 ====================

    /**
     * 热门话题总榜
     * Key: ranking:topics:hot
     * 
     * @return Redis key
     */
    public static String hotTopics() {
        return prefix() + ":topics:hot";
    }

    /**
     * 话题日榜
     * Key: ranking:topics:daily:{date}
     * 
     * @param date 日期
     * @return Redis key
     */
    public static String dailyTopics(LocalDate date) {
        return prefix() + ":topics:daily:" + date.toString();
    }

    /**
     * 话题周榜。
     */
    public static String weeklyTopics(int weekBasedYear, int weekNumber) {
        return prefix() + ":topics:weekly:" + weekBasedYear + ":" + String.format("%02d", weekNumber);
    }

    /**
     * 话题月榜。
     */
    public static String monthlyTopics(int year, int month) {
        return prefix() + ":topics:monthly:" + year + ":" + String.format("%02d", month);
    }

    // ==================== 防刷去重 ====================

    /**
     * 浏览去重 key（同一用户同一文章 30 分钟内只计一次）
     * Key: ranking:dedup:view:{postId}:{userId}
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return Redis key
     */
    public static String viewDedup(String postId, String userId) {
        return prefix() + ":dedup:view:" + postId + ":" + userId;
    }

    /**
     * 单篇文章浏览累计分数 key（用于分数上限检查）
     * Key: ranking:view:cap:{postId}
     *
     * @param postId 文章ID
     * @return Redis key
     */
    public static String viewScoreCap(String postId) {
        return prefix() + ":view:cap:" + postId;
    }

    // ==================== 空结果缓存 ====================

    /**
     * 空结果缓存标记 key（防缓存穿透）
     * Key: ranking:empty:{type}:{year}:{month}
     *
     * @param type 排行榜类型
     * @param year 年份
     * @param month 月份
     * @return Redis key
     */
    public static String emptyCache(String type, int year, int month) {
        return prefix() + ":empty:" + type + ":" + year + ":" + String.format("%02d", month);
    }

    // ==================== 分布式锁 ====================

    /**
     * 归档任务分布式锁
     * Key: ranking:lock:archive:{lockSuffix}
     *
     * @param lockSuffix 锁后缀（如 daily、weekly、monthly）
     * @return Redis key
     */
    public static String archiveLock(String lockSuffix) {
        return prefix() + ":lock:archive:" + lockSuffix;
    }

    /**
     * 定时刷新任务分布式锁
     * Key: ranking:lock:scheduler:{lockSuffix}
     *
     * @param lockSuffix 锁后缀（如 post-snapshot、creator-snapshot）
     * @return Redis key
     */
    public static String schedulerLock(String lockSuffix) {
        return prefix() + ":lock:scheduler:" + lockSuffix;
    }

    /**
     * ledger 全量补算互斥锁。
     *
     * <p>补算期间 ingestion 会据此拒绝新事件，调度器也会被一并排斥。</p>
     */
    public static String replayLock() {
        return prefix() + ":lock:replay:ledger-rebuild";
    }

    /**
     * 月榜回填加载锁
     * Key: ranking:lock:load:monthly:{year}-{month}
     *
     * @param year 年份
     * @param month 月份
     * @return Redis key
     */
    public static String monthlyLoadLock(int year, int month) {
        return prefix() + ":lock:load:monthly:" + year + "-" + month;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取日期对应的 ISO 周数
     * 
     * @return ISO 周数
     */
    public static int getWeekNumber(LocalDate date) {
        return IsoWeekUtils.getWeekNumber(date);
    }

    /**
     * 获取日期对应的 ISO week-based year。
     */
    public static int getWeekBasedYear(LocalDate date) {
        return IsoWeekUtils.getWeekBasedYear(date);
    }

    /**
     * 获取当前 ISO 周数。
     */
    public static int getCurrentWeekNumber() {
        return getWeekNumber(LocalDate.now());
    }

    /**
     * 获取当前 ISO week-based year。
     */
    public static int getCurrentWeekBasedYear() {
        return getWeekBasedYear(LocalDate.now());
    }
}
