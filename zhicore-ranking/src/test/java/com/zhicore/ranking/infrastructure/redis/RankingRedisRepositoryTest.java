package com.zhicore.ranking.infrastructure.redis;

import com.zhicore.ranking.domain.model.HotScore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排行榜 Redis 仓储测试
 * 
 * 测试范围：
 * 1. 月榜查询方法
 * 2. 日榜和周榜带分数查询方法
 * 3. Lua 脚本原子性更新
 * 4. TTL 设置逻辑
 * 5. 空数据处理
 * 6. 边界条件
 *
 * @author ZhiCore Team
 */
@SpringBootTest
@Testcontainers
@DisplayName("RankingRedisRepository Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RankingRedisRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RankingRedisRepository repository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // 清理所有测试数据
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @AfterEach
    void tearDown() {
        // 清理所有测试数据
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ==================== 月榜测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试 getMonthlyHotPosts() - 正常情况")
    void testGetMonthlyHotPosts_Normal() {
        // Given: 准备测试数据
        int year = 2024;
        int month = 1;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        // 添加测试数据（分数从高到低）
        repository.setScore(key, "post-1", 1000.0);
        repository.setScore(key, "post-2", 900.0);
        repository.setScore(key, "post-3", 800.0);
        repository.setScore(key, "post-4", 700.0);
        repository.setScore(key, "post-5", 600.0);

        // When: 查询月榜
        List<String> result = repository.getMonthlyHotPosts(year, month, 3);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("post-1", result.get(0));
        assertEquals("post-2", result.get(1));
        assertEquals("post-3", result.get(2));
    }

    @Test
    @Order(2)
    @DisplayName("测试 getMonthlyHotPosts() - 空数据")
    void testGetMonthlyHotPosts_EmptyData() {
        // Given: 没有数据
        int year = 2024;
        int month = 2;

        // When: 查询月榜
        List<String> result = repository.getMonthlyHotPosts(year, month, 10);

        // Then: 返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("测试 getMonthlyHotPosts() - Limit 边界")
    void testGetMonthlyHotPosts_LimitBoundary() {
        // Given: 准备10条数据
        int year = 2024;
        int month = 3;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        for (int i = 1; i <= 10; i++) {
            repository.setScore(key, "post-" + i, 1000.0 - i * 10);
        }

        // When: 测试不同的 limit 值
        List<String> result1 = repository.getMonthlyHotPosts(year, month, 0);
        List<String> result5 = repository.getMonthlyHotPosts(year, month, 5);
        List<String> result20 = repository.getMonthlyHotPosts(year, month, 20);

        // Then: 验证结果
        assertTrue(result1.isEmpty(), "limit=0 应该返回空列表");
        assertEquals(5, result5.size(), "limit=5 应该返回5条");
        assertEquals(10, result20.size(), "limit=20 但只有10条数据，应该返回10条");
    }

    @Test
    @Order(4)
    @DisplayName("测试 getMonthlyHotPostsWithScore() - 正常情况")
    void testGetMonthlyHotPostsWithScore_Normal() {
        // Given: 准备测试数据
        int year = 2024;
        int month = 4;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", 1000.0);
        repository.setScore(key, "post-2", 900.0);
        repository.setScore(key, "post-3", 800.0);

        // When: 查询带分数的月榜
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 3);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // 验证第一条记录
        HotScore first = result.get(0);
        assertEquals("post-1", first.getEntityId());
        assertEquals(1000.0, first.getScore(), 0.01);
        assertEquals(1, first.getRank());
        
        // 验证第二条记录
        HotScore second = result.get(1);
        assertEquals("post-2", second.getEntityId());
        assertEquals(900.0, second.getScore(), 0.01);
        assertEquals(2, second.getRank());
        
        // 验证第三条记录
        HotScore third = result.get(2);
        assertEquals("post-3", third.getEntityId());
        assertEquals(800.0, third.getScore(), 0.01);
        assertEquals(3, third.getRank());
    }

    @Test
    @Order(5)
    @DisplayName("测试 getMonthlyHotPostsWithScore() - 空数据")
    void testGetMonthlyHotPostsWithScore_EmptyData() {
        // Given: 没有数据
        int year = 2024;
        int month = 5;

        // When: 查询带分数的月榜
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 10);

        // Then: 返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== 日榜带分数测试 ====================

    @Test
    @Order(6)
    @DisplayName("测试 getDailyHotPostsWithScore() - 正常情况")
    void testGetDailyHotPostsWithScore_Normal() {
        // Given: 准备测试数据
        LocalDate date = LocalDate.of(2024, 1, 15);
        String key = RankingRedisKeys.dailyPosts(date);
        
        repository.setScore(key, "post-1", 500.0);
        repository.setScore(key, "post-2", 400.0);
        repository.setScore(key, "post-3", 300.0);

        // When: 查询日榜
        List<HotScore> result = repository.getDailyHotPostsWithScore(date, 3);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("post-1", result.get(0).getEntityId());
        assertEquals(500.0, result.get(0).getScore(), 0.01);
        assertEquals(1, result.get(0).getRank());
    }

    @Test
    @Order(7)
    @DisplayName("测试 getDailyHotPostsWithScore() - 空数据")
    void testGetDailyHotPostsWithScore_EmptyData() {
        // Given: 没有数据
        LocalDate date = LocalDate.of(2024, 1, 16);

        // When: 查询日榜
        List<HotScore> result = repository.getDailyHotPostsWithScore(date, 10);

        // Then: 返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== 周榜带分数测试 ====================

    @Test
    @Order(8)
    @DisplayName("测试 getWeeklyHotPostsWithScore() - 正常情况")
    void testGetWeeklyHotPostsWithScore_Normal() {
        // Given: 准备测试数据
        int weekNumber = 5;
        String key = RankingRedisKeys.weeklyPosts(weekNumber);
        
        repository.setScore(key, "post-1", 2000.0);
        repository.setScore(key, "post-2", 1800.0);
        repository.setScore(key, "post-3", 1600.0);

        // When: 查询周榜
        List<HotScore> result = repository.getWeeklyHotPostsWithScore(weekNumber, 3);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("post-1", result.get(0).getEntityId());
        assertEquals(2000.0, result.get(0).getScore(), 0.01);
        assertEquals(1, result.get(0).getRank());
    }

    @Test
    @Order(9)
    @DisplayName("测试 getWeeklyHotPostsWithScore() - 空数据")
    void testGetWeeklyHotPostsWithScore_EmptyData() {
        // Given: 没有数据
        int weekNumber = 6;

        // When: 查询周榜
        List<HotScore> result = repository.getWeeklyHotPostsWithScore(weekNumber, 10);

        // Then: 返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Lua 脚本测试 ====================

    @Test
    @Order(10)
    @DisplayName("测试 incrementPostScore() - 所有排行榜都被更新")
    void testIncrementPostScore_AllRankingsUpdated() {
        // Given: 准备测试
        String postId = "test-post-1";
        double delta = 10.0;

        // When: 调用增量更新
        repository.incrementPostScore(postId, delta);

        // Then: 验证所有4个排行榜都被更新
        String hotKey = RankingRedisKeys.hotPosts();
        String dailyKey = RankingRedisKeys.todayPosts();
        String weeklyKey = RankingRedisKeys.currentWeekPosts();
        String monthlyKey = RankingRedisKeys.currentMonthPosts();

        Double hotScore = repository.getScore(hotKey, postId);
        Double dailyScore = repository.getScore(dailyKey, postId);
        Double weeklyScore = repository.getScore(weeklyKey, postId);
        Double monthlyScore = repository.getScore(monthlyKey, postId);

        assertNotNull(hotScore, "总榜应该被更新");
        assertNotNull(dailyScore, "日榜应该被更新");
        assertNotNull(weeklyScore, "周榜应该被更新");
        assertNotNull(monthlyScore, "月榜应该被更新");

        assertEquals(delta, hotScore, 0.01);
        assertEquals(delta, dailyScore, 0.01);
        assertEquals(delta, weeklyScore, 0.01);
        assertEquals(delta, monthlyScore, 0.01);
    }

    @Test
    @Order(11)
    @DisplayName("测试 incrementPostScore() - 多次更新累加正确")
    void testIncrementPostScore_MultipleUpdates() {
        // Given: 准备测试
        String postId = "test-post-2";

        // When: 多次调用增量更新
        repository.incrementPostScore(postId, 10.0);
        repository.incrementPostScore(postId, 20.0);
        repository.incrementPostScore(postId, 30.0);

        // Then: 验证分数累加正确
        String hotKey = RankingRedisKeys.hotPosts();
        Double finalScore = repository.getScore(hotKey, postId);

        assertNotNull(finalScore);
        assertEquals(60.0, finalScore, 0.01, "分数应该累加为 10 + 20 + 30 = 60");
    }

    @Test
    @Order(12)
    @DisplayName("测试 incrementPostScore() - TTL 只在首次创建时设置")
    void testIncrementPostScore_TTLSetOnlyOnce() throws InterruptedException {
        // Given: 准备测试
        String postId = "test-post-3";
        String dailyKey = RankingRedisKeys.todayPosts();

        // When: 第一次更新
        repository.incrementPostScore(postId, 10.0);
        
        // 获取初始 TTL
        Long initialTTL = redisTemplate.getExpire(dailyKey);
        assertNotNull(initialTTL, "TTL 应该被设置");
        assertTrue(initialTTL > 0, "TTL 应该大于0");

        // 等待1秒
        Thread.sleep(1000);

        // 第二次更新
        repository.incrementPostScore(postId, 20.0);

        // 获取更新后的 TTL
        Long updatedTTL = redisTemplate.getExpire(dailyKey);
        assertNotNull(updatedTTL);

        // Then: TTL 应该减少（因为时间流逝），而不是重置
        assertTrue(updatedTTL < initialTTL, 
            "TTL 应该减少而不是重置，初始TTL=" + initialTTL + ", 更新后TTL=" + updatedTTL);
    }

    @Test
    @Order(13)
    @DisplayName("测试 incrementPostScore() - 原子性（所有操作要么全成功要么全失败）")
    void testIncrementPostScore_Atomicity() {
        // Given: 准备测试
        String postId = "test-post-4";
        double delta = 15.0;

        // When: 调用增量更新
        repository.incrementPostScore(postId, delta);

        // Then: 验证所有排行榜的分数一致（证明原子性）
        String hotKey = RankingRedisKeys.hotPosts();
        String dailyKey = RankingRedisKeys.todayPosts();
        String weeklyKey = RankingRedisKeys.currentWeekPosts();
        String monthlyKey = RankingRedisKeys.currentMonthPosts();

        Double hotScore = repository.getScore(hotKey, postId);
        Double dailyScore = repository.getScore(dailyKey, postId);
        Double weeklyScore = repository.getScore(weeklyKey, postId);
        Double monthlyScore = repository.getScore(monthlyKey, postId);

        // 所有分数应该相同
        assertEquals(hotScore, dailyScore, 0.01, "总榜和日榜分数应该一致");
        assertEquals(hotScore, weeklyScore, 0.01, "总榜和周榜分数应该一致");
        assertEquals(hotScore, monthlyScore, 0.01, "总榜和月榜分数应该一致");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @Order(14)
    @DisplayName("测试边界条件 - Limit 为 0")
    void testBoundaryCondition_LimitZero() {
        // Given: 准备测试数据
        int year = 2024;
        int month = 6;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        repository.setScore(key, "post-1", 100.0);

        // When: limit = 0
        List<String> result = repository.getMonthlyHotPosts(year, month, 0);

        // Then: 返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(15)
    @DisplayName("测试边界条件 - Limit 大于实际数据量")
    void testBoundaryCondition_LimitExceedsData() {
        // Given: 准备3条数据
        int year = 2024;
        int month = 7;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", 100.0);
        repository.setScore(key, "post-2", 90.0);
        repository.setScore(key, "post-3", 80.0);

        // When: limit = 100
        List<String> result = repository.getMonthlyHotPosts(year, month, 100);

        // Then: 只返回3条数据
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    @Order(16)
    @DisplayName("测试边界条件 - 分数相同时的排序")
    void testBoundaryCondition_SameScore() {
        // Given: 准备相同分数的数据
        int year = 2024;
        int month = 8;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", 100.0);
        repository.setScore(key, "post-2", 100.0);
        repository.setScore(key, "post-3", 100.0);

        // When: 查询数据
        List<String> result = repository.getMonthlyHotPosts(year, month, 10);

        // Then: 应该返回所有数据（Redis 会按字典序排序）
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    @Order(17)
    @DisplayName("测试边界条件 - 负数分数")
    void testBoundaryCondition_NegativeScore() {
        // Given: 准备包含负数分数的数据
        int year = 2024;
        int month = 9;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", 100.0);
        repository.setScore(key, "post-2", 0.0);
        repository.setScore(key, "post-3", -50.0);

        // When: 查询数据
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 10);

        // Then: 应该按分数降序排列
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("post-1", result.get(0).getEntityId());
        assertEquals("post-2", result.get(1).getEntityId());
        assertEquals("post-3", result.get(2).getEntityId());
    }

    @Test
    @Order(18)
    @DisplayName("测试边界条件 - 非常大的分数")
    void testBoundaryCondition_VeryLargeScore() {
        // Given: 准备非常大的分数
        int year = 2024;
        int month = 10;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", Double.MAX_VALUE);
        repository.setScore(key, "post-2", 1000000000.0);

        // When: 查询数据
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 10);

        // Then: 应该正确处理
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("post-1", result.get(0).getEntityId());
        assertEquals(Double.MAX_VALUE, result.get(0).getScore(), 0.01);
    }

    // ==================== 排名连续性测试 ====================

    @Test
    @Order(19)
    @DisplayName("测试排名连续性 - 排名从1开始连续递增")
    void testRankContinuity() {
        // Given: 准备测试数据
        int year = 2024;
        int month = 11;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        for (int i = 1; i <= 10; i++) {
            repository.setScore(key, "post-" + i, 1000.0 - i * 10);
        }

        // When: 查询数据
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 10);

        // Then: 验证排名连续性
        assertNotNull(result);
        assertEquals(10, result.size());
        
        for (int i = 0; i < result.size(); i++) {
            assertEquals(i + 1, result.get(i).getRank(), 
                "排名应该从1开始连续递增，第" + i + "个元素的排名应该是" + (i + 1));
        }
    }

    // ==================== 数据降序排列测试 ====================

    @Test
    @Order(20)
    @DisplayName("测试数据降序排列 - 分数严格降序")
    void testDescendingOrder() {
        // Given: 准备测试数据
        int year = 2024;
        int month = 12;
        String key = RankingRedisKeys.monthlyPosts(year, month);
        
        repository.setScore(key, "post-1", 1000.0);
        repository.setScore(key, "post-2", 900.0);
        repository.setScore(key, "post-3", 800.0);
        repository.setScore(key, "post-4", 700.0);
        repository.setScore(key, "post-5", 600.0);

        // When: 查询数据
        List<HotScore> result = repository.getMonthlyHotPostsWithScore(year, month, 10);

        // Then: 验证降序排列
        assertNotNull(result);
        assertEquals(5, result.size());
        
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).getScore() >= result.get(i + 1).getScore(),
                "分数应该降序排列，第" + i + "个元素的分数应该 >= 第" + (i + 1) + "个元素的分数");
        }
    }
}
