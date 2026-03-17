package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.service.query.CheckInQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInQueryController 测试")
class CheckInQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CheckInQueryService checkInQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CheckInQueryController(checkInQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功获取签到统计")
    void shouldGetCheckInStats() throws Exception {
        CheckInVO stats = new CheckInVO(LocalDate.of(2026, 3, 11), 10, 3, 5, true);
        when(checkInQueryService.getCheckInStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/users/{userId}/check-in/stats", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(10))
                .andExpect(jsonPath("$.data.continuousDays").value(3))
                .andExpect(jsonPath("$.data.checkedInToday").value(true));
    }
}
