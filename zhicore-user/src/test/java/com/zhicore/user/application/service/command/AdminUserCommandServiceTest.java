package com.zhicore.user.application.service.command;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserCommandService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserCommandService 测试")
class AdminUserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheStore cacheStore;

    @Spy
    private UserCacheKeyResolver userCacheKeyResolver = new com.zhicore.user.infrastructure.cache.DefaultUserCacheKeyResolver();

    @InjectMocks
    private AdminUserCommandService adminUserCommandService;

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
    @DisplayName("用户状态管理")
    class UserStatusManagement {

        @Test
        @DisplayName("应该成功禁用用户")
        void shouldDisableUserSuccessfully() {
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            adminUserCommandService.disableUser(123L);

            verify(userRepository).update(argThat(user -> user.getStatus() == UserStatus.DISABLED));
        }

        @Test
        @DisplayName("应该成功启用用户")
        void shouldEnableUserSuccessfully() {
            testUser.disable();
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            adminUserCommandService.enableUser(123L);

            verify(userRepository).update(argThat(user -> user.getStatus() == UserStatus.ACTIVE));
        }

        @Test
        @DisplayName("禁用不存在的用户应该抛出异常")
        void shouldThrowExceptionWhenDisablingNonexistentUser() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserCommandService.disableUser(999L));

            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("Token 失效")
    class TokenInvalidation {

        @Test
        @DisplayName("应该成功使用户 Token 失效")
        void shouldInvalidateUserTokensSuccessfully() {
            when(userRepository.existsById(123L)).thenReturn(true);

            adminUserCommandService.invalidateUserTokens(123L);

            verify(cacheStore).deletePattern(userCacheKeyResolver.userTokenPattern(123L));
        }

        @Test
        @DisplayName("使不存在用户的 Token 失效应该抛出异常")
        void shouldThrowExceptionWhenInvalidatingTokensForNonexistentUser() {
            when(userRepository.existsById(999L)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserCommandService.invalidateUserTokens(999L));

            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }
}
