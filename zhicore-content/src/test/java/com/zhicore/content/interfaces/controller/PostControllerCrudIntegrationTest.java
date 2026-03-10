package com.zhicore.content.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.interfaces.dto.request.AttachTagsRequest;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.request.SaveDraftRequest;
import com.zhicore.content.interfaces.dto.request.UpdatePostRequest;
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
 * PostController 端到端集成测试
 *
 * 覆盖范围：
 * - 创建/更新/发布/撤销发布/删除/恢复
 * - 草稿保存/读取/删除
 * - 标签 attach/detach/get
 * - 列表参数安全校验（部分典型场景）
 */
@AutoConfigureMockMvc
@DisplayName("PostController 端到端集成测试")
class PostControllerCrudIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/posts";
    private static final String USER_1 = "1000";
    private static final String USER_2 = "2000";
    private static final String VALID_UUIDV7_FILE_ID = "00000000-0000-7000-8000-000000000000";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    @Test
    @DisplayName("创建草稿->更新->发布->详情/列表可见->撤销发布后列表不可见")
    void createUpdatePublishAndList() throws Exception {
        Long postId = createPost(USER_1, "集成测试标题", "Hello **world**");

        // 更新文章
        UpdatePostRequest update = new UpdatePostRequest();
        update.setTitle("更新后的标题");
        update.setContent("更新后的内容");
        update.setCoverImageId(VALID_UUIDV7_FILE_ID);

        mockMvc.perform(
                        put(BASE + "/{postId}", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/content", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.postId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.contentType").value("markdown"))
                .andExpect(jsonPath("$.data.raw").value("更新后的内容"));

        // 发布
        mockMvc.perform(
                        post(BASE + "/{postId}/publish", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 详情可见且 publishedAt 不为空
        mockMvc.perform(get(BASE + "/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(postId))
                .andExpect(jsonPath("$.data.publishedAt", notNullValue()));

        // 列表可见（LATEST 默认 cursor 模式）
        mockMvc.perform(get(BASE).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(postId.intValue())));

        // 撤销发布
        mockMvc.perform(
                        post(BASE + "/{postId}/unpublish", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 已发布列表不可见
        mockMvc.perform(get(BASE).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(postId.intValue()))));
    }

    @Test
    @DisplayName("非作者更新文章应返回 403（权限语义）")
    void updateByNonOwnerShouldReturnForbidden() throws Exception {
        Long postId = createPost(USER_1, "标题", "内容");

        UpdatePostRequest update = new UpdatePostRequest();
        update.setTitle("越权更新");
        update.setContent("x");

        mockMvc.perform(
                        put(BASE + "/{postId}", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("草稿保存/读取/删除")
    void draftSaveGetDelete() throws Exception {
        Long postId = createPost(USER_1, "草稿标题", "初始内容");

        SaveDraftRequest save = new SaveDraftRequest();
        save.setContent("草稿内容 v1");
        save.setContentType("markdown");
        save.setIsAutoSave(false);

        mockMvc.perform(
                        post(BASE + "/{postId}/draft", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(save))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(
                        get(BASE + "/{postId}/draft", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").value("草稿内容 v1"));

        mockMvc.perform(
                        delete(BASE + "/{postId}/draft", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("标签 attach/detach/get（自动创建标签）")
    void attachDetachAndGetTags() throws Exception {
        Long postId = createPost(USER_1, "标签文章", "内容");

        AttachTagsRequest attach = new AttachTagsRequest();
        attach.setTags(List.of("Java", "Spring Boot"));

        mockMvc.perform(
                        post(BASE + "/{postId}/tags", postId)
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(attach))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/tags", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].slug", hasItems("java", "spring-boot")));

        // 移除一个标签
        mockMvc.perform(
                        delete(BASE + "/{postId}/tags/{slug}", postId, "java")
                                .header(CommonConstants.HEADER_USER_ID, USER_1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(BASE + "/{postId}/tags", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slug").value("spring-boot"));
    }

    @Test
    @DisplayName("列表参数非法不应 500：LATEST 禁止 page、POPULAR 禁止 cursor、非法 sort/status 返回 400")
    void listParamValidation() throws Exception {
        mockMvc.perform(get(BASE).param("sort", "LATEST").param("page", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(1001)));

        mockMvc.perform(get(BASE).param("sort", "POPULAR").param("cursor", "xxx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(1001)));

        mockMvc.perform(get(BASE).param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(1001)));

        mockMvc.perform(get(BASE).param("status", "DRAFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(1001)));
    }

    private Long createPost(String userId, String title, String content) throws Exception {
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

        // ApiResponse<Long>：data 即 postId
        return objectMapper.readTree(response).path("data").asLong();
    }
}
