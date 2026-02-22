package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.domain.model.UserCheckIn;
import com.zhicore.user.domain.model.UserCheckInStats;
import com.zhicore.user.domain.repository.UserCheckInRepository;
import com.zhicore.user.infrastructure.repository.mapper.UserCheckInMapper;
import com.zhicore.user.infrastructure.repository.po.UserCheckInPO;
import com.zhicore.user.infrastructure.repository.po.UserCheckInStatsPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 用户签到仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class UserCheckInRepositoryImpl implements UserCheckInRepository {

    private final UserCheckInMapper userCheckInMapper;

    @Override
    public void save(UserCheckIn checkIn) {
        UserCheckInPO po = toPO(checkIn);
        userCheckInMapper.insert(po);
    }

    @Override
    public boolean existsByUserIdAndDate(Long userId, LocalDate date) {
        return userCheckInMapper.existsByUserIdAndDate(userId, date);
    }

    @Override
    public Optional<UserCheckInStats> findStatsByUserId(Long userId) {
        UserCheckInStatsPO po = userCheckInMapper.selectStatsByUserId(userId);
        return Optional.ofNullable(toStatsDomain(po));
    }

    @Override
    public void saveOrUpdateStats(UserCheckInStats stats) {
        UserCheckInStatsPO existing = userCheckInMapper.selectStatsByUserId(stats.getUserId());
        UserCheckInStatsPO po = toStatsPO(stats);
        if (existing == null) {
            userCheckInMapper.insertStats(po);
        } else {
            userCheckInMapper.updateStats(po);
        }
    }

    private UserCheckInPO toPO(UserCheckIn checkIn) {
        UserCheckInPO po = new UserCheckInPO();
        po.setUserId(checkIn.getUserId());
        po.setCheckInDate(checkIn.getCheckInDate());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(checkIn.getCreatedAt()));
        return po;
    }

    private UserCheckInStats toStatsDomain(UserCheckInStatsPO po) {
        if (po == null) {
            return null;
        }
        return UserCheckInStats.reconstitute(
                po.getUserId(),
                po.getTotalDays() != null ? po.getTotalDays() : 0,
                po.getContinuousDays() != null ? po.getContinuousDays() : 0,
                po.getMaxContinuousDays() != null ? po.getMaxContinuousDays() : 0,
                po.getLastCheckInDate()
        );
    }

    private UserCheckInStatsPO toStatsPO(UserCheckInStats stats) {
        UserCheckInStatsPO po = new UserCheckInStatsPO();
        po.setUserId(stats.getUserId());
        po.setTotalDays(stats.getTotalDays());
        po.setContinuousDays(stats.getContinuousDays());
        po.setMaxContinuousDays(stats.getMaxContinuousDays());
        po.setLastCheckInDate(stats.getLastCheckInDate());
        return po;
    }
}
