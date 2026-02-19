package com.blog.user.domain.model;

import com.blog.common.util.DateTimeUtils;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户签到实体
 *
 * @author Blog Team
 */
@Getter
public class UserCheckIn {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 签到日期
     */
    private final LocalDate checkInDate;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    private UserCheckIn(Long userId, LocalDate checkInDate, LocalDateTime createdAt) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        Assert.notNull(checkInDate, "签到日期不能为空");
        
        this.userId = userId;
        this.checkInDate = checkInDate;
        this.createdAt = createdAt != null ? createdAt : DateTimeUtils.businessDateTime();
    }

    /**
     * 创建新的签到记录（使用业务时区）
     */
    public static UserCheckIn create(Long userId, LocalDate checkInDate) {
        return new UserCheckIn(userId, checkInDate, DateTimeUtils.businessDateTime());
    }

    /**
     * 从持久化恢复
     */
    public static UserCheckIn reconstitute(Long userId, LocalDate checkInDate, LocalDateTime createdAt) {
        return new UserCheckIn(userId, checkInDate, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserCheckIn that = (UserCheckIn) o;
        return Objects.equals(userId, that.userId) && 
               Objects.equals(checkInDate, that.checkInDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, checkInDate);
    }
}
