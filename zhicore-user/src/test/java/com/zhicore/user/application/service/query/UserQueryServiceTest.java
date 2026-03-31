package com.zhicore.user.application.service.query;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.query.view.UserSimpleView;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserFollowStats;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserQueryService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService 测试")
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        Set<Role> roles = new HashSet<>();
        roles.add(new Role(1, "USER", "普通用户"));

        testUser = User.reconstitute(new User.Snapshot(
                123L,
                "testuser",
                "测试用户",
                "test@example.com",
                "hashedPassword",
                "https://example.com/avatar.jpg",
                "这是个人简介",
                UserStatus.ACTIVE,
                true,
                true,
                roles,
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("应该成功获取用户详情和关注统计")
        void shouldGetUserSuccessfully() {
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(userFollowRepository.findStatsByUserId(123L))
                    .thenReturn(Optional.of(UserFollowStats.reconstitute(123L, 100, 50)));

            UserVO userVO = userQueryService.getUserById(123L);

            assertNotNull(userVO);
            assertEquals(123L, userVO.getId());
            assertEquals("testuser", userVO.getUserName());
            assertEquals("测试用户", userVO.getNickName());
            assertEquals(100, userVO.getFollowersCount());
            assertEquals(50, userVO.getFollowingCount());
        }

        @Test
        @DisplayName("用户不存在时应该抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    userQueryService.getUserById(999L));
            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }

    @Test
    @DisplayName("应该成功获取用户简要信息")
    void shouldGetUserSimpleSuccessfully() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

        UserSimpleView dto = userQueryService.getUserSimpleById(123L);

        assertEquals(123L, dto.getId());
        assertEquals("testuser", dto.getUserName());
        assertEquals("测试用户", dto.getNickname());
        verify(userRepository).findById(123L);
    }

    @Test
    @DisplayName("应该成功批量获取用户简要信息")
    void shouldBatchGetUsersSimpleSuccessfully() {
        when(userRepository.findByIds(Set.of(123L))).thenReturn(java.util.List.of(testUser));

        Map<Long, UserSimpleView> result = userQueryService.batchGetUsersSimple(Set.of(123L));

        assertEquals(1, result.size());
        assertEquals("testuser", result.get(123L).getUserName());
        verify(userRepository).findByIds(Set.of(123L));
    }

    @Test
    @DisplayName("应该成功获取陌生人消息设置")
    void shouldGetStrangerMessageSettingSuccessfully() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

        boolean result = userQueryService.isStrangerMessageAllowed(123L);

        assertTrue(result);
        verify(userRepository).findById(123L);
    }

    @Test
    @DisplayName("应该根据邮箱获取用户详情")
    void shouldGetUserByEmailSuccessfully() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        UserVO userVO = userQueryService.getUserByEmail("test@example.com");

        assertEquals(123L, userVO.getId());
        assertFalse(userVO.getNickName().isEmpty());
    }
}
