package com.zhicore.user.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserFollowStats;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.domain.service.UserDomainService;
import com.zhicore.user.infrastructure.feign.ZhiCoreUploadClient;
import com.zhicore.user.interfaces.dto.request.RegisterRequest;
import com.zhicore.user.interfaces.dto.request.UpdateProfileRequest;
import com.zhicore.user.domain.model.OutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserApplicationService 单元测试
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApplicationService 测试")
class UserApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ZhiCoreUploadClient zhiCoreUploadClient;

    @Mock
    private AuthApplicationService authApplicationService;

    @Mock
    private CacheRepository cacheRepository;

    @InjectMocks
    private UserApplicationService userApplicationService;

    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        Set<Role> roles = new HashSet<>();
        userRole = new Role(1, "USER", "普通用户");
        roles.add(userRole);

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
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }

    @Nested
    @DisplayName("用户注册")
    class Register {

        @Test
        @DisplayName("应该成功注册用户")
        void shouldRegisterUserSuccessfully() {
            // Given
            RegisterRequest request = new RegisterRequest();
            request.setUserName("newuser");
            request.setEmail("new@example.com");
            request.setPassword("password123");

            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenReturn(ApiResponse.success(456L));
            when(userDomainService.encodePassword("password123")).thenReturn("hashedPassword");
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Long userId = userApplicationService.register(request);

            // Then
            assertEquals(456L, userId);
            verify(userDomainService).validateRegistration("new@example.com", "newuser");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("邮箱已存在时应该抛出异常")
        void shouldThrowExceptionWhenEmailExists() {
            // Given
            RegisterRequest request = new RegisterRequest();
            request.setUserName("newuser");
            request.setEmail("existing@example.com");
            request.setPassword("password123");

            doThrow(new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS))
                    .when(userDomainService).validateRegistration(anyString(), anyString());

            // When & Then
            assertThrows(BusinessException.class, () ->
                    userApplicationService.register(request));
        }
    }

    @Nested
    @DisplayName("获取用户")
    class GetUser {

        @Test
        @DisplayName("应该成功获取用户信息")
        void shouldGetUserSuccessfully() {
            // Given
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(userFollowRepository.findStatsByUserId(123L))
                    .thenReturn(Optional.of(UserFollowStats.reconstitute(123L, 100, 50)));

            // When
            UserVO userVO = userApplicationService.getUserById(123L);

            // Then
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
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    userApplicationService.getUserById(999L));
            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("更新资料")
    class UpdateProfile {

        @BeforeEach
        void initSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("应该成功更新用户资料")
        void shouldUpdateProfileSuccessfully() {
            // Given
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickName("新昵称");
            request.setAvatarId("01933e5f-8b2a-7890-a123-456789abcdef");
            request.setBio("新的个人简介");

            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(userRepository.updateWithOptimisticLock(any(User.class))).thenReturn(testUser);

            // When
            userApplicationService.updateProfile(123L, request);

            // Then
            verify(userRepository).updateWithOptimisticLock(any(User.class));
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("用户不存在时应该抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNickName("新昵称");

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(BusinessException.class, () ->
                    userApplicationService.updateProfile(999L, request));
        }
    }

    @Nested
    @DisplayName("陌生人消息设置")
    class StrangerMessageSetting {

        @BeforeEach
        void initSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("应该成功获取陌生人消息设置")
        void shouldGetStrangerMessageSettingSuccessfully() {
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            boolean result = userApplicationService.isStrangerMessageAllowed(123L);

            assertTrue(result);
        }

        @Test
        @DisplayName("应该成功更新陌生人消息设置")
        void shouldUpdateStrangerMessageSettingSuccessfully() {
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            userApplicationService.updateStrangerMessageSetting(123L, false);

            assertFalse(testUser.isStrangerMessageAllowed());
            verify(userRepository).update(testUser);
        }
    }

    @Nested
    @DisplayName("用户状态管理")
    class StatusManagement {

        @BeforeEach
        void initSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void clearSynchronization() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("应该成功禁用用户")
        void shouldDisableUserSuccessfully() {
            // Given
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            // When
            userApplicationService.disableUser(123L);

            // Then
            verify(userRepository).update(any(User.class));
        }

        @Test
        @DisplayName("应该成功启用用户")
        void shouldEnableUserSuccessfully() {
            // Given
            testUser.disable(); // 先禁用
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            // When
            userApplicationService.enableUser(123L);

            // Then
            verify(userRepository).update(any(User.class));
        }
    }

    @Nested
    @DisplayName("角色分配")
    class RoleAssignment {

        @Test
        @DisplayName("应该成功分配角色")
        void shouldAssignRoleSuccessfully() {
            // Given
            Role adminRole = new Role(2, "ADMIN", "管理员");
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

            // When
            userApplicationService.assignRole(123L, "ADMIN");

            // Then
            verify(roleRepository).saveUserRole(123L, 2);
        }

        @Test
        @DisplayName("角色不存在时应该抛出异常")
        void shouldThrowExceptionWhenRoleNotFound() {
            // Given
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(roleRepository.findByName("NONEXISTENT")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(BusinessException.class, () ->
                    userApplicationService.assignRole(123L, "NONEXISTENT"));
        }
    }
}
