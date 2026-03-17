package com.zhicore.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IsoWeekUtils Tests")
class IsoWeekUtilsTest {

    @Test
    @DisplayName("应返回日期对应的 ISO week-based year 和 week number")
    void shouldResolveIsoWeekFields() {
        LocalDate date = LocalDate.of(2021, 1, 1);

        assertEquals(2020, IsoWeekUtils.getWeekBasedYear(date));
        assertEquals(53, IsoWeekUtils.getWeekNumber(date));
    }

    @Test
    @DisplayName("应正确判断某年是否存在第 53 周")
    void shouldValidateWeek53ByYear() {
        assertEquals(53, IsoWeekUtils.getMaxWeekNumber(2020));
        assertEquals(52, IsoWeekUtils.getMaxWeekNumber(2025));
        assertTrue(IsoWeekUtils.isValidWeek(2020, 53));
        assertFalse(IsoWeekUtils.isValidWeek(2025, 53));
    }
}
