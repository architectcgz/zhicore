package com.zhicore.content.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.service.PostReaderPresenceAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostReaderPresenceControllerTest {

    @Test
    @DisplayName("应返回阅读人数和头像列表")
    void shouldReturnPresencePayload() throws Exception {
        PostReaderPresenceAppService service = mock(PostReaderPresenceAppService.class);
        when(service.query(1001L)).thenReturn(PostReaderPresenceView.builder()
                .readingCount(3)
                .avatars(List.of(PostReaderPresenceView.ReaderAvatarView.builder()
                        .userId("2001")
                        .nickname("读者A")
                        .avatarUrl("https://cdn/avatar.png")
                        .build()))
                .build());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PostReaderPresenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/posts/1001/readers/presence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readingCount").value(3))
                .andExpect(jsonPath("$.data.avatars[0].nickname").value("读者A"));
    }

    @Test
    @DisplayName("leave 接口应透传 sessionId")
    void shouldForwardLeaveRequest() throws Exception {
        PostReaderPresenceAppService service = mock(PostReaderPresenceAppService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PostReaderPresenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/posts/1001/readers/session/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).leave(1001L, "session-a");
    }
}
