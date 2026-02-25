package com.zhicore.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserFollow 实体单元测试
 *
 * @author ZhiCore Team
 */
@DisplayName("UserFollow 实体测试")
class UserFollowTest {

    @Test
    @DisplayName("应该成功创建关注关系")
    void shouldCreateFollowSuccessfully() {
        // Given
        Long followerId = 1L;
        Long followingId = 2L;

        // When
        UserFollow follow = UserFollow.create(followerId, followingId);

        // Then
        assertNotNull(follow);
        assertEquals(followerId, follow.getFollowerId());
        assertEquals(followingId, follow.getFollowingId());
        assertNotNull(follow.getCreatedAt());
    }

    @Test
    @DisplayName("关注自己时应该抛出异常")
    void shouldThrowExceptionWhenFollowingSelf() {
        // Given
        Long userId = 1L;

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                UserFollow.create(userId, userId));
    }

    @Test
    @DisplayName("关注者ID为空时应该抛出异常")
    void shouldThrowExceptionWhenFollowerIdIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                UserFollow.create(null, 2L));
    }

    @Test
    @DisplayName("被关注者ID为空时应该抛出异常")
    void shouldThrowExceptionWhenFollowingIdIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                UserFollow.create(1L, null));
    }

    @Test
    @DisplayName("相同关注关系应该相等")
    void shouldBeEqualForSameFollowRelation() {
        // Given
        UserFollow follow1 = UserFollow.create(1L, 2L);
        UserFollow follow2 = UserFollow.create(1L, 2L);

        // When & Then
        assertEquals(follow1, follow2);
        assertEquals(follow1.hashCode(), follow2.hashCode());
    }

    @Test
    @DisplayName("不同关注关系应该不相等")
    void shouldNotBeEqualForDifferentFollowRelation() {
        // Given
        UserFollow follow1 = UserFollow.create(1L, 2L);
        UserFollow follow2 = UserFollow.create(1L, 3L);

        // When & Then
        assertNotEquals(follow1, follow2);
    }
}
