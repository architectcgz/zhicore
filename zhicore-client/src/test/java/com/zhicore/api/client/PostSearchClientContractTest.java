package com.zhicore.api.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostSearchClient 契约测试")
class PostSearchClientContractTest {

    @Test
    @DisplayName("搜索客户端应使用 content 的 /api/v1/posts 前缀")
    void shouldUseContentApiPrefix() throws NoSuchMethodException {
        assertGetMapping("getPostById", new Class<?>[]{Long.class}, "/api/v1/posts/{postId}");
        assertGetMapping("getPostSimple", new Class<?>[]{Long.class}, "/api/v1/posts/{postId}/simple");
        assertGetMapping("getPostsSimple", new Class<?>[]{List.class}, "/api/v1/posts/batch/simple");
        assertGetMapping("getPostAuthorId", new Class<?>[]{Long.class}, "/api/v1/posts/{postId}/author");
        assertGetMapping("getPublishedPostsByAuthor", new Class<?>[]{Long.class, Integer.class, Integer.class}, "/api/v1/posts/authors/{authorId}");
    }

    private void assertGetMapping(String methodName, Class<?>[] parameterTypes, String expectedPath)
            throws NoSuchMethodException {
        Method method = PostSearchClient.class.getMethod(methodName, parameterTypes);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertThat(mapping).as("%s should declare @GetMapping", methodName).isNotNull();
        assertThat(mapping.value()).containsExactly(expectedPath);
    }
}
