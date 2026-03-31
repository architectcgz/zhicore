package com.zhicore.user.application.service.query;

import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.application.port.store.FollowStatsStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowQueryService 测试")
class FollowQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private FollowStatsStore followStatsStore;

    @InjectMocks
    private FollowQueryService followQueryService;

    @Test
    @DisplayName("获取粉丝列表应批量查询用户并保持关注顺序")
    void getFollowers_shouldBatchLoadUsersAndKeepOrder() {
        when(userFollowRepository.findFollowers(10L, 1, 20)).thenReturn(List.of(
                UserFollow.reconstitute(2L, 10L, OffsetDateTime.now()),
                UserFollow.reconstitute(3L, 10L, OffsetDateTime.now())
        ));
        when(userRepository.findByIds(Set.of(2L, 3L))).thenReturn(List.of(
                buildUser(3L, "u3"),
                buildUser(2L, "u2")
        ));

        List<com.zhicore.user.application.dto.UserVO> result = followQueryService.getFollowers(10L, 1, 20);

        assertEquals(List.of(2L, 3L), result.stream().map(com.zhicore.user.application.dto.UserVO::getId).toList());
        verify(userRepository).findByIds(Set.of(2L, 3L));
        verify(userRepository, never()).findById(2L);
        verify(userRepository, never()).findById(3L);
    }

    @Test
    @DisplayName("获取空关注列表时应直接返回空集合")
    void getFollowings_shouldReturnEmptyWhenNoRelation() {
        when(userFollowRepository.findFollowings(10L, 1, 20)).thenReturn(List.of());

        List<com.zhicore.user.application.dto.UserVO> result = followQueryService.getFollowings(10L, 1, 20);

        assertTrue(result.isEmpty());
        verify(userRepository, never()).findByIds(Set.of());
    }

    private User buildUser(Long userId, String userName) {
        return User.reconstitute(new User.Snapshot(
                userId,
                userName,
                userName,
                userName + "@example.com",
                "hashed",
                null,
                null,
                UserStatus.ACTIVE,
                true,
                true,
                Set.of(new Role(1, "USER", "普通用户")),
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
    }
}
