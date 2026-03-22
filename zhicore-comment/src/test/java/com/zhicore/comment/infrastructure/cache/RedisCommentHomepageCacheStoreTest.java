package com.zhicore.comment.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.common.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("RedisCommentHomepageCacheStore Tests")
class RedisCommentHomepageCacheStoreTest {

    @Test
    @DisplayName("应能读取 Redis 中的首页评论快照")
    void shouldReadHomepageSnapshotFromRedisPayload() {
        RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = Mockito.mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(serializeAndDeserialize(sampleSnapshot()));

        RedisCommentHomepageCacheStore store = new RedisCommentHomepageCacheStore(
                redisTemplate,
                applicationObjectMapper()
        );

        Optional<PageResult<CommentVO>> result = store.get(1001L, CommentSortType.HOT, 20, 3);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getRecords()).hasSize(1);
        assertThat(result.orElseThrow().getRecords().get(0).getId()).isEqualTo(1L);
        assertThat(result.orElseThrow().getRecords().get(0).getHotReplies()).hasSize(1);
    }

    @Test
    @DisplayName("命中已反序列化快照对象时应直接复用")
    void shouldReuseAlreadyDeserializedSnapshot() {
        RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = Mockito.mock(ValueOperations.class);
        PageResult<CommentVO> snapshot = sampleSnapshot();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(snapshot);

        RedisCommentHomepageCacheStore store = new RedisCommentHomepageCacheStore(
                redisTemplate,
                applicationObjectMapper()
        );

        Optional<PageResult<CommentVO>> result = store.get(1001L, CommentSortType.TIME, 20, 3);

        assertThat(result).containsSame(snapshot);
    }

    private Object serializeAndDeserialize(PageResult<CommentVO> snapshot) {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(redisSerializerObjectMapper());
        return serializer.deserialize(serializer.serialize(snapshot));
    }

    private ObjectMapper applicationObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private ObjectMapper redisSerializerObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    private PageResult<CommentVO> sampleSnapshot() {
        CommentVO reply = CommentVO.builder()
                .id(2L)
                .postId(1001L)
                .rootId(1L)
                .content("reply")
                .createdAt(LocalDateTime.of(2026, 3, 21, 23, 0))
                .liked(false)
                .build();

        CommentVO root = CommentVO.builder()
                .id(1L)
                .postId(1001L)
                .content("root")
                .createdAt(LocalDateTime.of(2026, 3, 21, 22, 0))
                .liked(false)
                .hotReplies(List.of(reply))
                .build();

        return PageResult.of(0, 20, 1, List.of(root));
    }
}
