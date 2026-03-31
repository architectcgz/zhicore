package com.zhicore.content.interfaces.controller;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.service.PostQueryFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PostQueryController 测试")
class PostQueryControllerTest {

    @Test
    @DisplayName("应该成功获取作者已发布文章列表")
    void shouldGetPublishedPostsByAuthor() throws Exception {
        PostQueryFacade postQueryFacade = mock(PostQueryFacade.class);
        PostDTO post = new PostDTO();
        post.setId(1234567890123456789L);
        post.setOwnerId(2234567890123456789L);
        post.setTitle("作者公开文章");

        when(postQueryFacade.getPublishedPostsByAuthor(2234567890123456789L, 1, 20))
                .thenReturn(HybridPageResult.<PostDTO>builder()
                        .items(List.of(post))
                        .page(1)
                        .size(20)
                        .total(1L)
                        .pages(1)
                        .hasMore(false)
                        .paginationMode("offset")
                        .suggestCursorMode(false)
                        .build());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PostQueryController(postQueryFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/posts/authors/{authorId}", 2234567890123456789L)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.items[0].ownerId").value("2234567890123456789"))
                .andExpect(jsonPath("$.data.items[0].title").value("作者公开文章"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
