package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.service.query.FollowQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowQueryController 测试")
class FollowQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FollowQueryService followQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FollowQueryController(followQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功获取关注统计")
    void shouldGetFollowStats() throws Exception {
        FollowStatsVO stats = new FollowStatsVO();
        stats.setUserId(1L);
        stats.setFollowersCount(10);
        stats.setFollowingCount(5);
        when(followQueryService.getFollowStats(1L, 2L)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/users/{userId}/follow-stats", 1L).param("currentUserId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.followersCount").value(10))
                .andExpect(jsonPath("$.data.followingCount").value(5));
    }
}
