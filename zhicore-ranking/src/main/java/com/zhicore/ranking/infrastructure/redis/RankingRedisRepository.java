package com.zhicore.ranking.infrastructure.redis;

import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 排行榜 Redis 仓储
 *
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RankingRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Lua 脚本：原子性更新所有排行榜并设置 TTL
     * 
     * 优势：
     * 1. 原子性：所有操作在 Redis 服务端原子执行
     * 2. 性能：单次网络往返，从 7 次 Redis 命令减少到 1 次
     * 3. TTL 优化：只在 key 首次创建时设置过期时间
     */
    private static final String INCREMENT_SCRIPT = """
        -- KEYS: [1]hotKey [2]dailyKey [3]weeklyKey [4]monthlyKey
        -- ARGV: [1]postId [2]delta [3]dailyTTL [4]weeklyTTL [5]monthlyTTL
        
        -- 更新总榜（永久保留）
        redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[1])
        
        -- 更新日榜
        redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[1])
        if redis.call('TTL', KEYS[2]) == -1 then
            redis.call('EXPIRE', KEYS[2], ARGV[3])
        end
        
        -- 更新周榜
        redis.call('ZINCRBY', KEYS[3], ARGV[2], ARGV[1])
        if redis.call('TTL', KEYS[3]) == -1 then
            redis.call('EXPIRE', KEYS[3], ARGV[4])
        end
        
        -- 更新月榜
        redis.call('ZINCRBY', KEYS[4], ARGV[2], ARGV[1])
        if redis.call('TTL', KEYS[4]) == -1 then
            redis.call('EXPIRE', KEYS[4], ARGV[5])
        end
        
        return 1
        """;
    
    private final RedisScript<Long> incrementScript = 
        new DefaultRedisScript<>(INCREMENT_SCRIPT, Long.class);

    // ==================== 通用操作 ====================

    /**
     * 设置分数（覆盖）
     *
     * @param key Redis Key
     * @param member 成员
     * @param score 分数
     */
    public void setScore(String key, String member, double score) {
        redisTemplate.opsForZSet().add(key, member, score);
    }

    /**
     * 批量写入 Sorted Set（Pipeline + RENAME 保证原子可见性）
     *
     * <p>先写入临时 key，再 RENAME 覆盖目标 key，避免回填窗口期读到部分数据</p>
     *
     * @param key     目标 Redis Key
     * @param entries 成员-分数列表
     */
    public void batchSetScoreAtomic(String key, List<HotScore> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String tmpKey = key + ":tmp:" + System.currentTimeMillis();
        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                byte[] rawTmpKey = redisTemplate.getStringSerializer().serialize(tmpKey);
                for (HotScore entry : entries) {
                    byte[] rawMember = redisTemplate.getStringSerializer().serialize(entry.getEntityId());
                    connection.zSetCommands().zAdd(rawTmpKey, entry.getScore(), rawMember);
                }
                return null;
            });
            redisTemplate.rename(tmpKey, key);
        } catch (Exception e) {
            // 清理临时 key，避免残留
            redisTemplate.delete(tmpKey);
            throw e;
        }
    }

    /**
     * 增量更新分数
     *
     * @param key Redis Key
     * @param member 成员
     * @param delta 增量
     * @return 更新后的分数
     */
    public Double incrementScore(String key, String member, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, member, delta);
    }

    /**
     * 获取分数
     *
     * @param key Redis Key
     * @param member 成员
     * @return 分数
     */
    public Double getScore(String key, String member) {
        return redisTemplate.opsForZSet().score(key, member);
    }

    /**
     * 获取排名（从0开始，分数从高到低）
     *
     * @param key Redis Key
     * @param member 成员
     * @return 排名
     */
    public Long getRank(String key, String member) {
        return redisTemplate.opsForZSet().reverseRank(key, member);
    }

    /**
     * 获取排行榜（分数从高到低）
     *
     * @param key Redis Key
     * @param start 起始位置
     * @param end 结束位置
     * @return 排行榜列表
     */
    public List<HotScore> getTopRanking(String key, int start, int end) {
        Set<ZSetOperations.TypedTuple<Object>> tuples = 
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<HotScore> result = new ArrayList<>();
        int rank = start + 1;
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                result.add(HotScore.ofWithRank(
                        tuple.getValue().toString(),
                        tuple.getScore(),
                        rank++
                ));
            }
        }
        return result;
    }

    /**
     * 获取排行榜ID列表（分数从高到低）
     *
     * @param key Redis Key
     * @param start 起始位置
     * @param end 结束位置
     * @return ID列表
     */
    public List<String> getTopIds(String key, int start, int end) {
        Set<Object> members = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream()
                .map(Object::toString)
                .toList();
    }

    /**
     * 删除成员
     *
     * @param key Redis Key
     * @param member 成员
     */
    public void removeMember(String key, String member) {
        redisTemplate.opsForZSet().remove(key, member);
    }

    /**
     * 设置过期时间
     *
     * @param key Redis Key
     * @param duration 过期时间
     */
    public void setExpire(String key, Duration duration) {
        redisTemplate.expire(key, duration);
    }

    // ==================== 文章排行榜操作 ====================

    /**
     * 更新文章热度分数（总榜）
     *
     * @param postId 文章ID
     * @param score 分数
     */
    public void updatePostScore(String postId, double score) {
        setScore(RankingRedisKeys.hotPosts(), postId, score);
    }

    /**
     * 增量更新文章热度分数
     * 使用 Lua 脚本原子性更新所有排行榜
     *
     * @param postId 文章ID
     * @param delta 增量
     */
    public void incrementPostScore(String postId, double delta) {
        List<String> keys = List.of(
            RankingRedisKeys.hotPosts(),
            RankingRedisKeys.todayPosts(),
            RankingRedisKeys.currentWeekPosts(),
            RankingRedisKeys.currentMonthPosts()
        );
        
        redisTemplate.execute(
            incrementScript,
            keys,
            postId,
            String.valueOf(delta),
            String.valueOf(Duration.ofDays(2).getSeconds()),
            String.valueOf(Duration.ofDays(14).getSeconds()),
            String.valueOf(Duration.ofDays(365).getSeconds())
        );
    }

    /**
     * 获取热门文章排行（总榜）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 文章ID列表
     */
    public List<String> getHotPosts(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return getTopIds(RankingRedisKeys.hotPosts(), start, end);
    }

    /**
     * 获取热门文章排行（日榜）
     *
     * @param date 日期
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getDailyHotPosts(LocalDate date, int limit) {
        String key = RankingRedisKeys.dailyPosts(date);
        return getTopIds(key, 0, limit - 1);
    }

    /**
     * 获取热门文章排行（周榜）
     *
     * @param weekNumber 周数
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getWeeklyHotPosts(int weekNumber, int limit) {
        String key = RankingRedisKeys.weeklyPosts(weekNumber);
        return getTopIds(key, 0, limit - 1);
    }

    /**
     * 获取热门文章排行（月榜）
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getMonthlyHotPosts(int year, int month, int limit) {
        String key = RankingRedisKeys.monthlyPosts(year, month);
        return getTopIds(key, 0, limit - 1);
    }

    /**
     * 获取热门文章排行带分数（日榜）
     *
     * @param date 日期
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getDailyHotPostsWithScore(LocalDate date, int limit) {
        String key = RankingRedisKeys.dailyPosts(date);
        return getTopRanking(key, 0, limit - 1);
    }

    /**
     * 获取热门文章排行带分数（周榜）
     *
     * @param weekNumber 周数
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getWeeklyHotPostsWithScore(int weekNumber, int limit) {
        String key = RankingRedisKeys.weeklyPosts(weekNumber);
        return getTopRanking(key, 0, limit - 1);
    }

    /**
     * 获取热门文章排行带分数（月榜）
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getMonthlyHotPostsWithScore(int year, int month, int limit) {
        String key = RankingRedisKeys.monthlyPosts(year, month);
        return getTopRanking(key, 0, limit - 1);
    }

    // ==================== 创作者排行榜操作 ====================

    /**
     * 更新创作者热度分数
     *
     * @param userId 用户ID
     * @param score 分数
     */
    public void updateCreatorScore(String userId, double score) {
        setScore(RankingRedisKeys.hotCreators(), userId, score);
    }

    /**
     * 增量更新创作者热度分数
     *
     * @param userId 用户ID
     * @param delta 增量
     */
    public void incrementCreatorScore(String userId, double delta) {
        incrementScore(RankingRedisKeys.hotCreators(), userId, delta);
        
        // 更新日榜
        String dailyKey = RankingRedisKeys.dailyCreators(LocalDate.now());
        incrementScore(dailyKey, userId, delta);
        setExpire(dailyKey, Duration.ofDays(2));
    }

    /**
     * 获取创作者排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 用户ID列表
     */
    public List<String> getHotCreators(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return getTopIds(RankingRedisKeys.hotCreators(), start, end);
    }

    // ==================== 话题排行榜操作 ====================

    /**
     * 更新话题热度分数
     *
     * @param topicId 话题ID
     * @param score 分数
     */
    public void updateTopicScore(Long topicId, double score) {
        setScore(RankingRedisKeys.hotTopics(), topicId.toString(), score);
    }

    /**
     * 增量更新话题热度分数
     *
     * @param topicId 话题ID
     * @param delta 增量
     */
    public void incrementTopicScore(Long topicId, double delta) {
        String topicIdStr = topicId.toString();
        incrementScore(RankingRedisKeys.hotTopics(), topicIdStr, delta);
        
        // 更新日榜
        String dailyKey = RankingRedisKeys.dailyTopics(LocalDate.now());
        incrementScore(dailyKey, topicIdStr, delta);
        setExpire(dailyKey, Duration.ofDays(2));
    }

    /**
     * 获取热门话题排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 话题ID列表
     */
    public List<Long> getHotTopics(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return getTopIds(RankingRedisKeys.hotTopics(), start, end)
                .stream()
                .map(Long::parseLong)
                .toList();
    }
}
