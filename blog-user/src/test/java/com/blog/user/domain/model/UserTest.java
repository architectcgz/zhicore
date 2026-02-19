package com.blog.user.domain.model;

import com.blog.common.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User 聚合根单元测试
 *
 * @author Blog Team
 */
@DisplayName("User 聚合根测试")
class UserTest {

    @Nested
    @DisplayName("创建用户")
    class CreateUser {

        @Test
        @DisplayName("应该成功创建用户")
        void shouldCreateUserSuccessfully() {
            // Given
            Long id = 123L;
            String userName = "testuser";
            String email = "test@example.com";
            String passwordHash = "hashedPassword";

            // When
            User user = User.create(id, userName, email, passwordHash);

            // Then
            assertNotNull(user);
            assertEquals(id, user.getId());
            assertEquals(userName, user.getUserName());
            assertEquals(email, user.getEmail());
            assertEquals(passwordHash, user.getPasswordHash());
            assertEquals(userName, user.getNickName()); // 默认昵称为用户名
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertFalse(user.isEmailConfirmed());
            assertNotNull(user.getCreatedAt());
            assertNotNull(user.getRoles());
            assertTrue(user.getRoles().isEmpty());
        }

        @Test
        @DisplayName("用户ID为空时应该抛出异常")
        void shouldThrowExceptionWhenIdIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                    User.create(null, "testuser", "test@example.com", "hash"));
        }

        @Test
        @DisplayName("用户名为空时应该抛出异常")
        void shouldThrowExceptionWhenUserNameIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                    User.create(123L, "", "test@example.com", "hash"));
        }

        @Test
        @DisplayName("邮箱为空时应该抛出异常")
        void shouldThrowExceptionWhenEmailIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                    User.create(123L, "testuser", "", "hash"));
        }
    }

    @Nested
    @DisplayName("更新资料")
    class UpdateProfile {

        @Test
        @DisplayName("应该成功更新昵称")
        void shouldUpdateNickNameSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            String newNickName = "新昵称";

            // When
            user.updateProfile(newNickName, null, null);

            // Then
            assertEquals(newNickName, user.getNickName());
        }

        @Test
        @DisplayName("应该成功更新头像")
        void shouldUpdateAvatarSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            String newAvatarId = "01933e5f-8b2a-7890-a123-456789abcdef";

            // When
            user.updateProfile(null, newAvatarId, null);

            // Then
            assertEquals(newAvatarId, user.getAvatarId());
        }

        @Test
        @DisplayName("应该成功更新个人简介")
        void shouldUpdateBioSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            String newBio = "这是我的个人简介";

            // When
            user.updateProfile(null, null, newBio);

            // Then
            assertEquals(newBio, user.getBio());
        }

        @Test
        @DisplayName("昵称过长时应该抛出异常")
        void shouldThrowExceptionWhenNickNameTooLong() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            String longNickName = "a".repeat(51);

            // When & Then
            assertThrows(DomainException.class, () ->
                    user.updateProfile(longNickName, null, null));
        }

        @Test
        @DisplayName("个人简介过长时应该抛出异常")
        void shouldThrowExceptionWhenBioTooLong() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            String longBio = "a".repeat(501);

            // When & Then
            assertThrows(DomainException.class, () ->
                    user.updateProfile(null, null, longBio));
        }

        @Test
        @DisplayName("禁用用户更新资料时应该抛出异常")
        void shouldThrowExceptionWhenUserIsDisabled() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            user.disable();

            // When & Then
            assertThrows(DomainException.class, () ->
                    user.updateProfile("新昵称", null, null));
        }
    }

    @Nested
    @DisplayName("用户状态管理")
    class StatusManagement {

        @Test
        @DisplayName("应该成功禁用用户")
        void shouldDisableUserSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");

            // When
            user.disable();

            // Then
            assertEquals(UserStatus.DISABLED, user.getStatus());
            assertFalse(user.isActive());
        }

        @Test
        @DisplayName("应该成功启用用户")
        void shouldEnableUserSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            user.disable();

            // When
            user.enable();

            // Then
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertTrue(user.isActive());
        }

        @Test
        @DisplayName("重复禁用用户时应该抛出异常")
        void shouldThrowExceptionWhenDisablingAlreadyDisabledUser() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            user.disable();

            // When & Then
            assertThrows(DomainException.class, user::disable);
        }

        @Test
        @DisplayName("重复启用用户时应该抛出异常")
        void shouldThrowExceptionWhenEnablingAlreadyActiveUser() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");

            // When & Then
            assertThrows(DomainException.class, user::enable);
        }
    }

    @Nested
    @DisplayName("邮箱验证")
    class EmailConfirmation {

        @Test
        @DisplayName("应该成功确认邮箱")
        void shouldConfirmEmailSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");

            // When
            user.confirmEmail();

            // Then
            assertTrue(user.isEmailConfirmed());
        }

        @Test
        @DisplayName("重复确认邮箱时应该抛出异常")
        void shouldThrowExceptionWhenEmailAlreadyConfirmed() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            user.confirmEmail();

            // When & Then
            assertThrows(DomainException.class, user::confirmEmail);
        }
    }

    @Nested
    @DisplayName("角色管理")
    class RoleManagement {

        @Test
        @DisplayName("应该成功分配角色")
        void shouldAssignRoleSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            Role role = new Role(1, "USER", "普通用户");

            // When
            user.assignRole(role);

            // Then
            assertTrue(user.getRoles().contains(role));
            assertTrue(user.hasRole("USER"));
        }

        @Test
        @DisplayName("应该成功移除角色")
        void shouldRemoveRoleSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");
            Role role = new Role(1, "USER", "普通用户");
            user.assignRole(role);

            // When
            user.removeRole(role);

            // Then
            assertFalse(user.getRoles().contains(role));
            assertFalse(user.hasRole("USER"));
        }

        @Test
        @DisplayName("分配空角色时应该抛出异常")
        void shouldThrowExceptionWhenAssigningNullRole() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "hash");

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    user.assignRole(null));
        }
    }

    @Nested
    @DisplayName("修改密码")
    class ChangePassword {

        @Test
        @DisplayName("应该成功修改密码")
        void shouldChangePasswordSuccessfully() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "oldHash");
            String newPasswordHash = "newHash";

            // When
            user.changePassword(newPasswordHash);

            // Then
            assertEquals(newPasswordHash, user.getPasswordHash());
        }

        @Test
        @DisplayName("新密码为空时应该抛出异常")
        void shouldThrowExceptionWhenNewPasswordIsEmpty() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "oldHash");

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    user.changePassword(""));
        }

        @Test
        @DisplayName("禁用用户修改密码时应该抛出异常")
        void shouldThrowExceptionWhenUserIsDisabled() {
            // Given
            User user = User.create(123L, "testuser", "test@example.com", "oldHash");
            user.disable();

            // When & Then
            assertThrows(DomainException.class, () ->
                    user.changePassword("newHash"));
        }
    }
}
