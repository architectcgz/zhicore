package com.blog.user.application.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.PageResult;
import com.blog.common.result.ResultCode;
import com.blog.user.domain.model.Role;
import com.blog.user.domain.model.User;
import com.blog.user.domain.model.UserStatus;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.interfaces.dto.response.UserManageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminUserApplicationService 单元测试
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserApplicationService 测试")
class AdminUserApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private AdminUserApplicationService adminUserApplicationService;

    private User testUser;
    private Set<Role> testRoles;

    @BeforeEach
    void setUp() {
        testRoles = new HashSet<>();
        testRoles.add(new Role(1, "USER", "普通用户"));

        testUser = User.reconstitute(
                123L,
                "testuser",
                "测试用户",
                "test@example.com",
                "hashedPassword",
                "https://example.com/avatar.jpg",
                "这是个人简介",
                UserStatus.ACTIVE,
                true,
                testRoles,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("queryUsers 测试")
    class QueryUsers {

        @Test
        @DisplayName("应该成功查询用户列表（正常场景）")
        void shouldQueryUsersSuccessfully() {
            // Given
            String keyword = "test";
            String status = "ACTIVE";
            int page = 1;
            int size = 20;
            
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(eq(keyword), eq(status), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(eq(keyword), eq(status)))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(keyword, status, page, size);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("testuser", result.getRecords().get(0).getUsername());
            assertEquals("test@example.com", result.getRecords().get(0).getEmail());
            assertEquals("ACTIVE", result.getRecords().get(0).getStatus());
            
            verify(userRepository).findByConditions(keyword, status, 0, 20);
            verify(userRepository).countByConditions(keyword, status);
        }

        @Test
        @DisplayName("应该处理无效页码（使用默认值）")
        void shouldHandleInvalidPage() {
            // Given
            int invalidPage = -1;
            int size = 20;
            
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, invalidPage, size);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getCurrent()); // 应该使用默认页码 1
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该处理无效页面大小（使用默认值）")
        void shouldHandleInvalidSize() {
            // Given
            int page = 1;
            int invalidSize = -10;
            
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, page, invalidSize);

            // Then
            assertNotNull(result);
            assertEquals(20, result.getSize()); // 应该使用默认大小 20
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该处理超大页面大小（限制为最大值）")
        void shouldHandleOversizedPageSize() {
            // Given
            int page = 1;
            int oversizedSize = 200;
            
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(100)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, page, oversizedSize);

            // Then
            assertNotNull(result);
            assertEquals(100, result.getSize()); // 应该限制为最大值 100
            verify(userRepository).findByConditions(null, null, 0, 100);
        }

        @Test
        @DisplayName("应该拒绝超长关键词")
        void shouldRejectLongKeyword() {
            // Given
            String longKeyword = "a".repeat(150); // 超过 100 字符
            int page = 1;
            int size = 20;

            // When & Then
            // ValidationException 会被 catch 块捕获并包装成 BusinessException
            assertThrows(BusinessException.class, () ->
                    adminUserApplicationService.queryUsers(longKeyword, null, page, size));
            
            verify(userRepository, never()).findByConditions(anyString(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("应该处理空结果")
        void shouldHandleEmptyResult() {
            // Given
            String keyword = "nonexistent";
            int page = 1;
            int size = 20;
            
            when(userRepository.findByConditions(eq(keyword), isNull(), eq(0), eq(20)))
                    .thenReturn(Collections.emptyList());
            when(userRepository.countByConditions(eq(keyword), isNull()))
                    .thenReturn(0L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(keyword, null, page, size);

            // Then
            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("应该正确处理 null 关键词")
        void shouldHandleNullKeyword() {
            // Given
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, 1, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该正确处理空字符串关键词")
        void shouldHandleEmptyKeyword() {
            // Given
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers("", null, 1, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该正确处理空白字符串关键词")
        void shouldHandleWhitespaceKeyword() {
            // Given
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers("   ", null, 1, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该正确规范化状态参数")
        void shouldNormalizeStatusParameter() {
            // Given
            String status = "active"; // 小写
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), eq("ACTIVE"), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), eq("ACTIVE")))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, status, 1, 20);

            // Then
            assertNotNull(result);
            verify(userRepository).findByConditions(null, "ACTIVE", 0, 20);
        }

        @Test
        @DisplayName("应该正确计算分页偏移量")
        void shouldCalculateOffsetCorrectly() {
            // Given
            int page = 3;
            int size = 20;
            int expectedOffset = 40; // (3-1) * 20
            
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(expectedOffset), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(100L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, page, size);

            // Then
            assertNotNull(result);
            verify(userRepository).findByConditions(null, null, expectedOffset, 20);
        }

        @Test
        @DisplayName("应该正确转换为 DTO")
        void shouldConvertToDTOCorrectly() {
            // Given
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, 1, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            
            UserManageDTO dto = result.getRecords().get(0);
            assertEquals(testUser.getId(), dto.getId());
            assertEquals(testUser.getUserName(), dto.getUsername());
            assertEquals(testUser.getEmail(), dto.getEmail());
            assertEquals(testUser.getNickName(), dto.getNickname());
            assertEquals(testUser.getAvatarId(), dto.getAvatar());
            assertEquals(testUser.getStatus().name(), dto.getStatus());
            assertNotNull(dto.getCreatedAt());
            assertNotNull(dto.getRoles());
            assertEquals(1, dto.getRoles().size());
            assertTrue(dto.getRoles().contains("USER"));
        }

        @Test
        @DisplayName("应该处理多个用户的查询")
        void shouldHandleMultipleUsers() {
            // Given
            User user2 = User.reconstitute(
                    456L,
                    "testuser2",
                    "测试用户2",
                    "test2@example.com",
                    "hashedPassword",
                    null,
                    null,
                    UserStatus.ACTIVE,
                    true,
                    testRoles,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            
            List<User> mockUsers = Arrays.asList(testUser, user2);
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(2L);

            // When
            PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(null, null, 1, 20);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getTotal());
            assertEquals(2, result.getRecords().size());
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandling {

        @Test
        @DisplayName("应该处理 Repository 异常")
        void shouldHandleRepositoryException() {
            // Given
            when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserApplicationService.queryUsers("test", null, 1, 20));
            
            assertEquals(ResultCode.INTERNAL_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("查询用户列表失败"));
        }

        @Test
        @DisplayName("应该处理统计异常")
        void shouldHandleCountException() {
            // Given
            List<User> mockUsers = Arrays.asList(testUser);
            when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockUsers);
            when(userRepository.countByConditions(any(), any()))
                    .thenThrow(new RuntimeException("Count error"));

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserApplicationService.queryUsers("test", null, 1, 20));
            
            assertEquals(ResultCode.INTERNAL_ERROR.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("用户状态管理测试")
    class UserStatusManagement {

        @Test
        @DisplayName("应该成功禁用用户")
        void shouldDisableUserSuccessfully() {
            // Given
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            // When
            adminUserApplicationService.disableUser(123L);

            // Then
            verify(userRepository).update(argThat(user -> 
                user.getStatus() == UserStatus.DISABLED
            ));
        }

        @Test
        @DisplayName("应该成功启用用户")
        void shouldEnableUserSuccessfully() {
            // Given
            testUser.disable(); // 先禁用
            when(userRepository.findById(123L)).thenReturn(Optional.of(testUser));

            // When
            adminUserApplicationService.enableUser(123L);

            // Then
            verify(userRepository).update(argThat(user -> 
                user.getStatus() == UserStatus.ACTIVE
            ));
        }

        @Test
        @DisplayName("禁用不存在的用户应该抛出异常")
        void shouldThrowExceptionWhenDisablingNonexistentUser() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserApplicationService.disableUser(999L));
            
            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("Token 失效测试")
    class TokenInvalidation {

        @Test
        @DisplayName("应该成功使用户 Token 失效")
        void shouldInvalidateUserTokensSuccessfully() {
            // Given
            when(userRepository.existsById(123L)).thenReturn(true);
            when(stringRedisTemplate.keys(anyString())).thenReturn(new HashSet<>());

            // When
            adminUserApplicationService.invalidateUserTokens(123L);

            // Then
            verify(stringRedisTemplate, atLeastOnce()).delete(anyString());
        }

        @Test
        @DisplayName("使不存在用户的 Token 失效应该抛出异常")
        void shouldThrowExceptionWhenInvalidatingTokensForNonexistentUser() {
            // Given
            when(userRepository.existsById(999L)).thenReturn(false);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    adminUserApplicationService.invalidateUserTokens(999L));
            
            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        }
    }
}
