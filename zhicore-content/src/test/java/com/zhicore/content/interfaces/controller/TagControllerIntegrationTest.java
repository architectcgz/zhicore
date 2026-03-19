package com.zhicore.content.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.interfaces.dto.request.AttachTagsRequest;
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
 * TagController 端到端集成测试
 */
@AutoConfigureMockMvc
@DisplayName("TagController 端到端集成测试")
class TagControllerIntegrationTest extends IntegrationTestBase {

    private static final String POST_BASE = "/api/v1/posts";
    private static final String TAG_BASE = "/api/v1/tags";
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
    @DisplayName("标签详情/列表/搜索/按标签查文章")
    void tagEndpointsShouldWork() throws Exception {
        Long postId = createAndPublishPost(USER_1, "标签文章", "内容");

        AttachTagsRequest attach = new AttachTagsRequest();
        attach.setTags(List.of("Spring Boot", "Java"));

        mockMvc.perform(
                        post(POST_BASE + "/{postId}/tags", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(attach))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long springBootTagId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE slug = ?",
                Long.class,
                "spring-boot"
        );

        // 详情
        mockMvc.perform(get(TAG_BASE + "/{slug}", "spring-boot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(springBootTagId)))
                .andExpect(jsonPath("$.data.slug").value("spring-boot"));

        // 列表
        mockMvc.perform(get(TAG_BASE).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].slug", hasItem("spring-boot")));

        // 搜索
        mockMvc.perform(get(TAG_BASE + "/search").param("keyword", "spring").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].slug", hasItem("spring-boot")));

        // 按标签查文章
        mockMvc.perform(get(TAG_BASE + "/{slug}/posts", "spring-boot").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(String.valueOf(postId))));
    }

    @Test
    @DisplayName("热门标签：按 tag_stats 排序输出")
    void hotTagsShouldReturnFromTagStats() throws Exception {
        // 准备数据：创建两个 tag，并写入 tag_stats
        Long postId = createAndPublishPost(USER_1, "热门标签文章", "内容");
        AttachTagsRequest attach = new AttachTagsRequest();
        attach.setTags(List.of("Java", "Spring Boot"));
        mockMvc.perform(
                        post(POST_BASE + "/{postId}/tags", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(attach))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 直接写 tag_stats，确保热门接口可预测
        jdbcTemplate.update("DELETE FROM tag_stats");
        jdbcTemplate.update("INSERT INTO tag_stats(tag_id, post_count, updated_at) VALUES ((SELECT id FROM tags WHERE slug='java'), 10, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO tag_stats(tag_id, post_count, updated_at) VALUES ((SELECT id FROM tags WHERE slug='spring-boot'), 5, CURRENT_TIMESTAMP)");
        Long javaTagId = jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE slug = ?",
                Long.class,
                "java"
        );

        mockMvc.perform(get(TAG_BASE + "/hot").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(javaTagId)))
                .andExpect(jsonPath("$.data[0].slug").value("java"));
    }

    private Long createAndPublishPost(String userId, String title, String content) throws Exception {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle(title);
        req.setContent(content);
        req.setContentType("markdown");

        var result = mockMvc.perform(
                        post(POST_BASE)
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

        var dataNode = objectMapper.readTree(response).path("data");
        if (!dataNode.isTextual()) {
            throw new AssertionError("创建文章返回的 postId 不是字符串: " + response);
        }
        Long postId = dataNode.asLong();

        mockMvc.perform(post(POST_BASE + "/{postId}/publish", postId).header(CommonConstants.HEADER_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        return postId;
    }
}
