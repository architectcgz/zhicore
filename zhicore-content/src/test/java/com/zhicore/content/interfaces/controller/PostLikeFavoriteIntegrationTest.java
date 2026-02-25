package com.zhicore.content.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 点赞/收藏端到端集成测试
 *
 * 重点覆盖：
 * - 仅允许对已发布文章点赞/收藏
 * - 状态查询与计数（Redis MISS -> DB -> 回填）
 * - 批量状态查询（Pipeline + DB 回填）
 */
@AutoConfigureMockMvc
@DisplayName("点赞/收藏端到端集成测试")
class PostLikeFavoriteIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/posts";
    private static final String USER_1 = "1000";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    @Test
    @DisplayName("点赞：like->status=true->count=1；重复点赞返回业务失败")
    void likeFlow() throws Exception {
        Long postId = createAndPublishPost(USER_1, "点赞文章", "内容");

        mockMvc.perform(post(BASE + "/{postId}/like", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/like/status", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get(BASE + "/{postId}/like/count", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        // 重复点赞：按当前约定，HTTP 200 + code!=200
        mockMvc.perform(post(BASE + "/{postId}/like", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", not(200)));
    }

    @Test
    @DisplayName("取消点赞：未点赞先取消返回业务失败；点赞后取消成功且状态=false")
    void unlikeFlow() throws Exception {
        Long postId = createAndPublishPost(USER_1, "取消点赞文章", "内容");

        mockMvc.perform(delete(BASE + "/{postId}/like", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", not(200)));

        mockMvc.perform(post(BASE + "/{postId}/like", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete(BASE + "/{postId}/like", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/like/status", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("批量点赞状态：包含 Redis 命中与 DB 回填分支")
    void batchLikeStatus() throws Exception {
        Long post1 = createAndPublishPost(USER_1, "批量1", "内容");
        Long post2 = createAndPublishPost(USER_1, "批量2", "内容");

        // 对 post1 点赞，使其进入 Redis 命中分支
        mockMvc.perform(post(BASE + "/{postId}/like", post1).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 清空 Redis，让 post1 走 DB 回填；post2 仍为 false
        cleanupRedis();

        mockMvc.perform(
                        post(BASE + "/like/batch-status")
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(List.of(post1, post2)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['" + post1 + "']").value(true))
                .andExpect(jsonPath("$.data['" + post2 + "']").value(false));
    }

    @Test
    @DisplayName("收藏：favorite->status=true->count=1；取消后 status=false")
    void favoriteFlow() throws Exception {
        Long postId = createAndPublishPost(USER_1, "收藏文章", "内容");

        mockMvc.perform(post(BASE + "/{postId}/favorite", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/favorite/status", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get(BASE + "/{postId}/favorite/count", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(delete(BASE + "/{postId}/favorite", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/favorite/status", postId).header(CommonConstants.HEADER_USER_ID, USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));
    }

    private Long createAndPublishPost(String userId, String title, String content) throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle(title);
        req.setContent(content);
        req.setContentType("markdown");

        var result = mockMvc.perform(
                        post(BASE)
                                .header(CommonConstants.HEADER_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andReturn();

        int status = result.getResponse().getStatus();
        String response = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        if (status != 200) {
            throw new AssertionError("创建文章失败，HTTP=" + status + ", body=" + response);
        }

        Long postId = objectMapper.readTree(response).path("data").asLong();

        mockMvc.perform(post(BASE + "/{postId}/publish", postId).header(CommonConstants.HEADER_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        return postId;
    }
}
