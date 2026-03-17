package com.zhicore.user.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.port.store.CheckInStore;
import com.zhicore.user.domain.model.UserCheckIn;
import com.zhicore.user.domain.model.UserCheckInStats;
import com.zhicore.user.domain.repository.UserCheckInRepository;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 签到命令服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInCommandService {

    private final UserCheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final CheckInStore checkInStore;

    @Transactional
    public CheckInVO checkIn(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        LocalDate today = DateTimeUtils.businessDate();
        if (hasCheckedInToday(userId, today)) {
            throw new BusinessException(ResultCode.ALREADY_CHECKED_IN);
        }

        if (checkInRepository.existsByUserIdAndDate(userId, today)) {
            throw new BusinessException(ResultCode.ALREADY_CHECKED_IN);
        }

        UserCheckIn checkIn = UserCheckIn.create(userId, today);
        checkInRepository.save(checkIn);

        UserCheckInStats stats = checkInRepository.findStatsByUserId(userId)
                .orElse(UserCheckInStats.create(userId));
        stats.recordCheckIn(today);
        checkInRepository.saveOrUpdateStats(stats);

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

    private boolean hasCheckedInToday(Long userId, LocalDate date) {
        try {
            return checkInStore.hasCheckedIn(userId, date);
        } catch (Exception e) {
            log.warn("Redis Bitmap 查询失败: {}", e.getMessage());
            return false;
        }
    }

    private void setCheckInBitmap(Long userId, LocalDate date) {
        try {
            checkInStore.markCheckedIn(userId, date);
        } catch (Exception e) {
            log.warn("Redis Bitmap 设置失败: {}", e.getMessage());
        }
    }
}
