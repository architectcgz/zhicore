package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.service.command.CheckInCommandService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInCommandController 测试")
class CheckInCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CheckInCommandService checkInCommandService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CheckInCommandController(checkInCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功签到")
    void shouldCheckIn() throws Exception {
        CheckInVO result = new CheckInVO(LocalDate.of(2026, 3, 11), 10, 3, 5, true);
        when(checkInCommandService.checkIn(1L)).thenReturn(result);

        mockMvc.perform(post("/api/v1/users/{userId}/check-in", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(10))
                .andExpect(jsonPath("$.data.checkedInToday").value(true));
    }
}
