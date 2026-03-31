package com.zhicore.user.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UploadFileDeleteClient;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.integration.messaging.user.UserProfileUpdatedIntegrationEvent;
import com.zhicore.user.application.command.RegisterCommand;
import com.zhicore.user.application.command.UpdateProfileCommand;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.event.UserIntegrationEventPort;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserCommandService 单元测试
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandService 测试")
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private UploadFileDeleteClient uploadFileDeleteClient;

    @Mock
    private AuthCommandService authCommandService;

    @Mock
    private CacheStore cacheStore;

    @Mock
    private UserIntegrationEventPort userIntegrationEventPort;

    private UserCacheKeyResolver userCacheKeyResolver;
    private UserCommandService userCommandService;

    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userCacheKeyResolver = new com.zhicore.user.infrastructure.cache.DefaultUserCacheKeyResolver();
        userCommandService = new UserCommandService(
                userRepository,
                roleRepository,
                userDomainService,
                idGeneratorFeignClient,
                uploadFileDeleteClient,
                authCommandService,
                cacheStore,
                userCacheKeyResolver,
                new TransactionCommitSignal(),
                userIntegrationEventPort
        );

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
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));
    }

    @Nested
    @DisplayName("用户注册")
    class Register {

        @Test
        @DisplayName("应该成功注册用户")
        void shouldRegisterUserSuccessfully() {
            // Given
            RegisterCommand request = new RegisterCommand("newuser", "new@example.com", "password123");

            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenReturn(ApiResponse.success(456L));
            when(userDomainService.encodePassword("password123")).thenReturn("hashedPassword");
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Long userId = userCommandService.register(request);

            // Then
            assertEquals(456L, userId);
            verify(userDomainService).validateRegistration("new@example.com", "newuser");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("邮箱已存在时应该抛出异常")
        void shouldThrowExceptionWhenEmailExists() {
            // Given
            RegisterCommand request = new RegisterCommand("newuser", "existing@example.com", "password123");

            doThrow(new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS))
                    .when(userDomainService).validateRegistration(anyString(), anyString());

            // When & Then
            assertThrows(BusinessException.class, () ->
                    userCommandService.register(request));
        }
    }

    @Nested
    @DisplayName("更新资料")
    class UpdateProfile {

        @BeforeEach
        void initSynchronization() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.initSynchronization();
            }
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
            UpdateProfileCommand request = new UpdateProfileCommand(
                    "新昵称",
                    "01933e5f-8b2a-7890-a123-456789abcdef",
                    "新的个人简介"
            );

            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));
            when(userRepository.updateWithOptimisticLock(any(User.class))).thenReturn(testUser);

            // When
            userCommandService.updateProfile(123L, request);

            // Then
            verify(userRepository).updateWithOptimisticLock(any(User.class));
            verify(userIntegrationEventPort).publish(any(UserProfileUpdatedIntegrationEvent.class));
        }

        @Test
        @DisplayName("用户不存在时应该抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            UpdateProfileCommand request = new UpdateProfileCommand("新昵称", null, null);

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(BusinessException.class, () ->
                    userCommandService.updateProfile(999L, request));
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
        @DisplayName("应该成功更新陌生人消息设置")
        void shouldUpdateStrangerMessageSettingSuccessfully() {
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            userCommandService.updateStrangerMessageSetting(123L, false);

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
            userCommandService.disableUser(123L);

            // Then
            verify(userRepository).update(any(User.class));
            verify(authCommandService).revokeAllRefreshTokens(123L);
        }

        @Test
        @DisplayName("应该成功启用用户")
        void shouldEnableUserSuccessfully() {
            // Given
            testUser.disable(); // 先禁用
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            // When
            userCommandService.enableUser(123L);

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
            userCommandService.assignRole(123L, "ADMIN");

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
                    userCommandService.assignRole(123L, "NONEXISTENT"));
        }
    }
}
