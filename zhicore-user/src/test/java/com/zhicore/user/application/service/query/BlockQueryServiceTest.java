package com.zhicore.user.application.service.query;

import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockQueryService 测试")
class BlockQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @InjectMocks
    private BlockQueryService blockQueryService;

    @Test
    @DisplayName("获取拉黑列表应批量查询用户并保持拉黑顺序")
    void getBlockedUsers_shouldBatchLoadUsersAndKeepOrder() {
        when(userBlockRepository.findByBlockerId(10L, 1, 20)).thenReturn(List.of(
                UserBlock.reconstitute(10L, 4L, LocalDateTime.now()),
                UserBlock.reconstitute(10L, 5L, LocalDateTime.now())
        ));
        when(userRepository.findByIds(Set.of(4L, 5L))).thenReturn(List.of(
                buildUser(5L, "u5"),
                buildUser(4L, "u4")
        ));

        List<com.zhicore.user.application.dto.UserVO> result = blockQueryService.getBlockedUsers(10L, 1, 20);

        assertEquals(List.of(4L, 5L), result.stream().map(com.zhicore.user.application.dto.UserVO::getId).toList());
        verify(userRepository).findByIds(Set.of(4L, 5L));
        verify(userRepository, never()).findById(4L);
        verify(userRepository, never()).findById(5L);
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
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }
}
