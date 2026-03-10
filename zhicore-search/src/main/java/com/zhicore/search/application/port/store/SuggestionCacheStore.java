package com.zhicore.search.application.port.store;

import java.time.Duration;
import java.util.List;

/**
 * 搜索建议缓存存储端口。
 *
 * 封装热门搜索词和用户搜索历史的缓存访问细节，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface SuggestionCacheStore {

    /**
     * 读取热门搜索词。
     *
     * @param limit 返回数量上限
     * @return 热门搜索词列表
     */
    List<String> getHotKeywords(int limit);

    /**
     * 热门搜索词计数自增。
     *
     * @param keyword 关键词
     */
    void incrementHotKeywordScore(String keyword);

    /**
     * 获取热门搜索词总量。
     *
     * @return 词条数量
     */
    long getHotKeywordCount();

    /**
     * 清理热度最低的一段热门搜索词。
     *
     * @param start 起始下标
     * @param end 结束下标
     */
    void removeHotKeywordRange(long start, long end);

    /**
     * 读取用户搜索历史。
     *
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @return 搜索历史列表
     */
    List<String> getUserHistory(String userId, int limit);

    /**
     * 记录用户搜索历史。
     *
     * @param userId 用户ID
     * @param keyword 关键词
     * @param maxHistorySize 历史上限
     * @param ttl 过期时间
     */
    void recordUserHistory(String userId, String keyword, int maxHistorySize, Duration ttl);

    /**
     * 清除用户搜索历史。
     *
     * @param userId 用户ID
     */
    void clearUserHistory(String userId);
}
