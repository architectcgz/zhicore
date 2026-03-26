package com.zhicore.user.interfaces.controller;

import com.zhicore.api.dto.user.FollowerShardItemDTO;
import com.zhicore.api.dto.user.FollowerShardPageDTO;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.query.FollowQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowQueryController 粉丝分片测试")
class FollowQueryControllerFollowerShardTest {

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
    @DisplayName("应该成功获取粉丝分片")
    void shouldGetFollowerShard() throws Exception {
        FollowerShardItemDTO item = new FollowerShardItemDTO();
        item.setFollowerId(6L);
        item.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));

        FollowerShardPageDTO page = new FollowerShardPageDTO();
        page.setItems(List.of(item));
        page.setNextCursorFollowerId(6L);

        when(followQueryService.getFollowerShard(200L, 5L, 2000)).thenReturn(page);

        mockMvc.perform(get("/api/v1/users/{userId}/followers/shard", 200L)
                        .param("cursorFollowerId", "5")
                        .param("size", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].followerId").value("6"))
                .andExpect(jsonPath("$.data.nextCursorFollowerId").value("6"));

        verify(followQueryService).getFollowerShard(200L, 5L, 2000);
    }
}
