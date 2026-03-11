package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.query.view.UserManageView;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserManageQueryService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserManageQueryService 测试")
class UserManageQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserManageQueryService userManageQueryService;

    private User testUser;
    private Set<Role> testRoles;

    @BeforeEach
    void setUp() {
        testRoles = new HashSet<>();
        testRoles.add(new Role(1, "USER", "普通用户"));

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
                testRoles,
                0L,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
    }

    @Nested
    @DisplayName("queryUsers")
    class QueryUsers {

        @Test
        @DisplayName("应该成功查询用户列表")
        void shouldQueryUsersSuccessfully() {
            when(userRepository.findByConditions(eq("test"), eq("ACTIVE"), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(eq("test"), eq("ACTIVE")))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers("test", "ACTIVE", 1, 20);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("testuser", result.getRecords().get(0).getUsername());
            assertEquals("test@example.com", result.getRecords().get(0).getEmail());
            verify(userRepository).findByConditions("test", "ACTIVE", 0, 20);
            verify(userRepository).countByConditions("test", "ACTIVE");
        }

        @Test
        @DisplayName("应该处理无效页码")
        void shouldHandleInvalidPage() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers(null, null, -1, 20);

            assertEquals(1, result.getCurrent());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该处理无效页面大小")
        void shouldHandleInvalidSize() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers(null, null, 1, -10);

            assertEquals(20, result.getSize());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该处理超大页面大小")
        void shouldHandleOversizedPageSize() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(100)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers(null, null, 1, 200);

            assertEquals(100, result.getSize());
            verify(userRepository).findByConditions(null, null, 0, 100);
        }

        @Test
        @DisplayName("应该拒绝超长关键词")
        void shouldRejectLongKeyword() {
            assertThrows(BusinessException.class, () ->
                    userManageQueryService.queryUsers("a".repeat(150), null, 1, 20));

            verify(userRepository, never()).findByConditions(any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("应该处理空结果")
        void shouldHandleEmptyResult() {
            when(userRepository.findByConditions(eq("nonexistent"), isNull(), eq(0), eq(20)))
                    .thenReturn(Collections.emptyList());
            when(userRepository.countByConditions(eq("nonexistent"), isNull()))
                    .thenReturn(0L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers("nonexistent", null, 1, 20);

            assertEquals(0, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("应该正确处理空关键词")
        void shouldHandleEmptyKeyword() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers("   ", null, 1, 20);

            assertEquals(1, result.getTotal());
            verify(userRepository).findByConditions(null, null, 0, 20);
        }

        @Test
        @DisplayName("应该正确规范化状态参数")
        void shouldNormalizeStatusParameter() {
            when(userRepository.findByConditions(isNull(), eq("ACTIVE"), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), eq("ACTIVE")))
                    .thenReturn(1L);

            userManageQueryService.queryUsers(null, "active", 1, 20);

            verify(userRepository).findByConditions(null, "ACTIVE", 0, 20);
        }

        @Test
        @DisplayName("应该正确计算分页偏移量")
        void shouldCalculateOffsetCorrectly() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(40), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(100L);

            userManageQueryService.queryUsers(null, null, 3, 20);

            verify(userRepository).findByConditions(null, null, 40, 20);
        }

        @Test
        @DisplayName("应该正确转换为查询视图")
        void shouldConvertToViewCorrectly() {
            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(1L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers(null, null, 1, 20);

            UserManageView view = result.getRecords().get(0);
            assertEquals(testUser.getId(), view.getId());
            assertEquals(testUser.getUserName(), view.getUsername());
            assertEquals(testUser.getEmail(), view.getEmail());
            assertEquals(testUser.getNickName(), view.getNickname());
            assertEquals(testUser.getAvatarId(), view.getAvatar());
            assertEquals(testUser.getStatus().name(), view.getStatus());
            assertEquals(1, view.getRoles().size());
            assertTrue(view.getRoles().contains("USER"));
        }

        @Test
        @DisplayName("应该处理多个用户的查询")
        void shouldHandleMultipleUsers() {
            User user2 = User.reconstitute(new User.Snapshot(
                    456L,
                    "testuser2",
                    "测试用户2",
                    "test2@example.com",
                    "hashedPassword",
                    null,
                    null,
                    UserStatus.ACTIVE,
                    true,
                    true,
                    testRoles,
                    0L,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            ));

            when(userRepository.findByConditions(isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(Arrays.asList(testUser, user2));
            when(userRepository.countByConditions(isNull(), isNull()))
                    .thenReturn(2L);

            PageResult<UserManageView> result = userManageQueryService.queryUsers(null, null, 1, 20);

            assertEquals(2, result.getTotal());
            assertEquals(2, result.getRecords().size());
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("应该处理 Repository 异常")
        void shouldHandleRepositoryException() {
            when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Database error"));

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    userManageQueryService.queryUsers("test", null, 1, 20));

            assertEquals(ResultCode.INTERNAL_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("查询用户列表失败"));
        }

        @Test
        @DisplayName("应该处理统计异常")
        void shouldHandleCountException() {
            when(userRepository.findByConditions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(testUser));
            when(userRepository.countByConditions(any(), any()))
                    .thenThrow(new RuntimeException("Count error"));

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    userManageQueryService.queryUsers("test", null, 1, 20));

            assertEquals(ResultCode.INTERNAL_ERROR.getCode(), exception.getCode());
        }
    }
}
