package com.zhicore.user.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.port.store.CheckInStore;
import com.zhicore.user.application.sentinel.UserSentinelHandlers;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import com.zhicore.user.domain.model.UserCheckInStats;
import com.zhicore.user.domain.repository.UserCheckInRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * 签到查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInQueryService {

    private final UserCheckInRepository checkInRepository;
    private final CheckInStore checkInStore;

    @SentinelResource(
            value = UserSentinelResources.GET_CHECK_IN_STATS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetCheckInStatsBlocked"
    )
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

    @SentinelResource(
            value = UserSentinelResources.GET_MONTHLY_CHECK_IN_BITMAP,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetMonthlyCheckInBitmapBlocked"
    )
    public long getMonthlyCheckInBitmap(Long userId, YearMonth yearMonth) {
        try {
            return checkInStore.getMonthlyBitmap(userId, yearMonth);
        } catch (Exception e) {
            log.warn("获取月度签到记录失败: {}", e.getMessage());
            return 0L;
        }
    }
}
