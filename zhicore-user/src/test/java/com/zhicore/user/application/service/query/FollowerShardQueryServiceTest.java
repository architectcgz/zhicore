package com.zhicore.user.application.service.query;

import com.zhicore.user.application.dto.FollowerShardPageVO;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowerShardQueryService 测试")
class FollowerShardQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private FollowStatsStore followStatsStore;

    @InjectMocks
    private FollowQueryService followQueryService;

    @Test
    @DisplayName("获取粉丝分片应按 followerId 稳定返回游标页")
    void getFollowerShard_shouldReturnStableCursorPage() {
        when(userFollowRepository.findFollowerShard(200L, 5L, 2)).thenReturn(List.of(
                UserFollow.reconstitute(6L, 200L, OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8))),
                UserFollow.reconstitute(8L, 200L, OffsetDateTime.of(2026, 3, 1, 10, 1, 0, 0, ZoneOffset.ofHours(8)))
        ));

        FollowerShardPageVO result = followQueryService.getFollowerShard(200L, 5L, 2);

        assertEquals(List.of(6L, 8L), result.getItems().stream().map(item -> item.getFollowerId()).toList());
        assertEquals(8L, result.getNextCursorFollowerId());
        verify(userFollowRepository).findFollowerShard(200L, 5L, 2);
    }

    @Test
    @DisplayName("获取粉丝分片应限制最大分页大小并在空页返回空游标")
    void getFollowerShard_shouldClampPageSizeAndReturnNullCursorWhenEmpty() {
        when(userFollowRepository.findFollowerShard(200L, 0L, 2000)).thenReturn(List.of());

        FollowerShardPageVO result = followQueryService.getFollowerShard(200L, -1L, 5000);

        assertEquals(List.of(), result.getItems());
        assertNull(result.getNextCursorFollowerId());
        verify(userFollowRepository).findFollowerShard(200L, 0L, 2000);
    }
}
