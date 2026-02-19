package com.blog.user.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 签到视图对象
 *
 * @author Blog Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInVO {

    /**
     * 签到日期
     */
    private LocalDate checkInDate;

    /**
     * 总签到天数
     */
    private Integer totalDays;

    /**
     * 连续签到天数
     */
    private Integer continuousDays;

    /**
     * 最大连续签到天数
     */
    private Integer maxContinuousDays;

    /**
     * 是否今日已签到
     */
    private Boolean checkedInToday;
}
