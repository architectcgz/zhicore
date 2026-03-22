package com.zhicore.comment.infrastructure.cache;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.comment.domain.model.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisCommentDetailCacheStore 兼容性测试")
class RedisCommentDetailCacheStoreTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ObjectMapper applicationObjectMapper;
    private RedisCommentDetailCacheStore cacheStore;

    @BeforeEach
    void setUp() {
        applicationObjectMapper = createApplicationObjectMapper();
        cacheStore = new RedisCommentDetailCacheStore(redisTemplate, applicationObjectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("开发阶段应通过版本化 key 直接失效旧详情缓存")
    void shouldUseVersionedDetailCacheKey() {
        assertEquals("comment:1:detail:v2", CommentRedisKeys.detail(1L));
    }

    @Test
    @DisplayName("应从详情缓存快照还原领域对象")
    void shouldReadDomainCommentFromSnapshot() {
        Comment comment = Comment.createTopLevel(1L, 100L, 200L, "test comment", null, null, null);
        when(valueOperations.get(CommentRedisKeys.detail(1L)))
                .thenReturn(CommentDetailCacheSnapshot.from(comment));

        Comment cached = cacheStore.get(1L).getValue();

        assertEquals(1L, cached.getId());
        assertEquals(100L, cached.getPostId());
        assertTrue(cached.isTopLevel());
    }

    @Test
    @DisplayName("写入详情缓存时不应直接序列化领域对象")
    void shouldStoreStableSnapshotInsteadOfDomainComment() {
        Comment comment = Comment.createTopLevel(1L, 100L, 200L, "test comment", null, null, null);

        cacheStore.set(comment.getId(), comment, Duration.ofSeconds(60));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq(CommentRedisKeys.detail(1L)), valueCaptor.capture(), eq(60L), eq(TimeUnit.SECONDS));

        Object storedValue = valueCaptor.getValue();
        Map<?, ?> storedMap = applicationObjectMapper.convertValue(storedValue, Map.class);

        assertFalse(storedValue instanceof Comment);
        assertFalse(storedMap.containsKey("topLevel"));
        assertFalse(storedMap.containsKey("deleted"));
        assertFalse(storedMap.containsKey("reply"));
    }

    private ObjectMapper createApplicationObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
}
