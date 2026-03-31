package com.zhicore.user.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.query.UserPostQueryService;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.query.view.UserSimpleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserQueryController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryController 测试")
class UserQueryControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private UserPostQueryService userPostQueryService;

    @BeforeEach
    void setUp() {
        UserQueryController controller = new UserQueryController(userQueryPort, userPostQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功获取陌生人消息设置")
    void shouldGetStrangerMessageSetting() throws Exception {
        when(userQueryPort.isStrangerMessageAllowed(1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/users/{userId}/settings/stranger-message", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("应该成功获取用户详细信息并返回字符串ID")
    void shouldGetUser() throws Exception {
        UserVO user = new UserVO();
        user.setId(1234567890123456789L);
        user.setUserName("detail-user");
        user.setNickName("详情用户");
        when(userQueryPort.getUserById(1234567890123456789L)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/{userId}", 1234567890123456789L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.userName").value("detail-user"))
                .andExpect(jsonPath("$.data.nickName").value("详情用户"));
    }

    @Test
    @DisplayName("应该成功获取用户简要信息")
    void shouldGetUserSimple() throws Exception {
        UserSimpleView view = new UserSimpleView();
        view.setId(1234567890123456789L);
        view.setUserName("testuser");
        view.setNickname("测试用户");
        view.setAvatarId("avatar-1");
        view.setProfileVersion(3L);
        when(userQueryPort.getUserSimpleById(1234567890123456789L)).thenReturn(view);

        mockMvc.perform(get("/api/v1/users/{userId}/simple", 1234567890123456789L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.userName").value("testuser"))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.avatarId").value("avatar-1"))
                .andExpect(jsonPath("$.data.profileVersion").value(3));
    }

    @Test
    @DisplayName("应该成功批量获取用户简要信息")
    void shouldBatchGetUsersSimple() throws Exception {
        UserSimpleView view = new UserSimpleView();
        view.setId(1234567890123456789L);
        view.setUserName("testuser");
        view.setNickname("测试用户");
        when(userQueryPort.batchGetUsersSimple(java.util.Set.of(1234567890123456789L)))
                .thenReturn(java.util.Map.of(1234567890123456789L, view));

        mockMvc.perform(post("/api/v1/users/batch/simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Set.of(1234567890123456789L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.1234567890123456789.id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.1234567890123456789.userName").value("testuser"))
                .andExpect(jsonPath("$.data.1234567890123456789.nickname").value("测试用户"));
    }

    @Test
    @DisplayName("应该成功获取用户公开文章列表")
    void shouldGetUserPublishedPosts() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(1234567890123456789L);
        post.setOwnerId(2234567890123456789L);
        post.setTitle("公开文章");

        HybridPageResult<PostDTO> pageResult = HybridPageResult.<PostDTO>builder()
                .items(java.util.List.of(post))
                .page(1)
                .size(20)
                .total(1L)
                .pages(1)
                .hasMore(false)
                .paginationMode("offset")
                .suggestCursorMode(false)
                .build();

        when(userPostQueryService.getPublishedPostsByAuthor(2234567890123456789L, 1, 20)).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/users/{userId}/posts", 2234567890123456789L)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.items[0].ownerId").value("2234567890123456789"))
                .andExpect(jsonPath("$.data.items[0].title").value("公开文章"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
