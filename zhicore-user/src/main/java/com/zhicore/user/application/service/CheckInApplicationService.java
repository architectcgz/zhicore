package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.domain.model.UserCheckIn;
import com.zhicore.user.domain.model.UserCheckInStats;
import com.zhicore.user.domain.repository.UserCheckInRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 签到应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInApplicationService {

    private final UserCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 用户签到
     *
     * @param userId 用户ID
     * @return 签到结果
     */
    @Transactional
    public CheckInVO checkIn(Long userId) {
        // 0. 验证用户是否存在
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 使用业务时区获取当前日期，确保签到判断基于统一时区
        LocalDate today = DateTimeUtils.businessDate();

        // 1. 检查今日是否已签到（先查 Redis Bitmap）
        if (hasCheckedInToday(userId, today)) {
            throw new BusinessException(ResultCode.ALREADY_CHECKED_IN);
        }

        // 2. 数据库层面再次检查（防止并发）
        if (checkInRepository.existsByUserIdAndDate(userId, today)) {
            throw new BusinessException(ResultCode.ALREADY_CHECKED_IN);
        }

        // 3. 保存签到记录 - 不再需要生成ID，使用复合主键
        UserCheckIn checkIn = UserCheckIn.create(userId, today);
        checkInRepository.save(checkIn);

        // 4. 更新签到统计
        UserCheckInStats stats = checkInRepository.findStatsByUserId(userId)
                .orElse(UserCheckInStats.create(userId));
        stats.recordCheckIn(today);
        checkInRepository.saveOrUpdateStats(stats);

        // 5. 更新 Redis Bitmap
        setCheckInBitmap(userId, today);

        log.info("User checked in: userId={}, date={}, continuousDays={}", 
                userId, today, stats.getContinuousDays());

        return new CheckInVO(
                today,
                stats.getTotalDays(),
                stats.getContinuousDays(),
                stats.getMaxContinuousDays(),
                true
        );
    }

    /**
     * 获取签到统计
     *
     * @param userId 用户ID
     * @return 签到统计
     */
    @Transactional(readOnly = true)
    public CheckInVO getCheckInStats(Long userId) {
        UserCheckInStats stats = checkInRepository.findStatsByUserId(userId)
                .orElse(UserCheckInStats.create(userId));

        return new CheckInVO(
                stats.getLastCheckInDate(),
                stats.getTotalDays(),
                stats.getContinuousDays(),
                stats.getMaxContinuousDays(),
                stats.hasCheckedInToday()
        );
    }

    /**
     * 检查今日是否已签到（使用 Redis Bitmap）
     *
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已签到
     */
    private boolean hasCheckedInToday(Long userId, LocalDate date) {
        try {
            String key = UserRedisKeys.checkInBitmap(userId, YearMonth.from(date));
            int dayOfMonth = date.getDayOfMonth();
            Boolean bit = redisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
            return Boolean.TRUE.equals(bit);
        } catch (Exception e) {
            log.warn("Redis Bitmap 查询失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置签到 Bitmap
     *
     * @param userId 用户ID
     * @param date 日期
     */
    private void setCheckInBitmap(Long userId, LocalDate date) {
        try {
            String key = UserRedisKeys.checkInBitmap(userId, YearMonth.from(date));
            int dayOfMonth = date.getDayOfMonth();
            redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        } catch (Exception e) {
            log.warn("Redis Bitmap 设置失败: {}", e.getMessage());
        }
    }

    /**
     * 获取月度签到记录（Bitmap）
     *
     * @param userId 用户ID
     * @param yearMonth 年月
     * @return 签到位图（32位整数，每位代表一天）
     */
    public long getMonthlyCheckInBitmap(Long userId, YearMonth yearMonth) {
        try {
            String key = UserRedisKeys.checkInBitmap(userId, yearMonth);
            // 获取整个月的签到位图
            byte[] bytes = (byte[]) redisTemplate.execute(connection -> 
                    connection.stringCommands().get(key.getBytes()), true);
            
            if (bytes == null || bytes.length == 0) {
                return 0L;
            }
            
            // 转换为 long
            long bitmap = 0L;
            for (int i = 0; i < bytes.length && i < 8; i++) {
                bitmap |= ((long) (bytes[i] & 0xFF)) << (i * 8);
            }
            return bitmap;
        } catch (Exception e) {
            log.warn("获取月度签到记录失败: {}", e.getMessage());
            return 0L;
        }
    }
}
