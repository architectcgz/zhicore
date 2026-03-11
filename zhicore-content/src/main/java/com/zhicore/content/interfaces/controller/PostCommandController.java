package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.service.PostCommandService;
import com.zhicore.content.interfaces.dto.request.AttachTagsRequest;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.request.SaveDraftRequest;
import com.zhicore.content.interfaces.dto.request.SchedulePublishRequest;
import com.zhicore.content.interfaces.dto.request.UpdatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章写控制器。
 */
@Tag(name = "文章写接口", description = "文章创建、发布、草稿、标签等写操作接口")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostCommandController {

    private final PostCommandService postCommandService;

    @Operation(summary = "创建文章", description = "创建新文章，默认状态为草稿")
    @PostMapping
    public ApiResponse<Long> createPost(@Valid @RequestBody CreatePostRequest request) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postCommandService.createPost(userId, new CreatePostAppCommand(
                request.getTitle(),
                request.getContent(),
                request.getContentType(),
                request.getTopicId(),
                request.getCoverImageId(),
                request.getTags()
        )));
    }

    @Operation(summary = "更新文章", description = "更新文章内容、标题、封面等信息")
    @PutMapping("/{postId}")
    public ApiResponse<Void> updatePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody UpdatePostRequest request) {
        Long userId = UserContext.requireUserId();
        postCommandService.updatePost(userId, postId, new UpdatePostAppCommand(
                request.getTitle(),
                request.getContent(),
                request.getTopicId(),
                request.getCoverImageId(),
                request.getTags()
        ));
        return ApiResponse.success();
    }

    @Operation(summary = "发布文章", description = "将草稿状态的文章发布为公开可见")
    @PostMapping("/{postId}/publish")
    public ApiResponse<Void> publishPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.publishPost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/unpublish")
    public ApiResponse<Void> unpublishPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.unpublishPost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/schedule")
    public ApiResponse<Void> schedulePublish(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody SchedulePublishRequest request) {
        Long userId = UserContext.requireUserId();
        postCommandService.schedulePublish(userId, postId, request.getScheduledAt());
        return ApiResponse.success();
    }

    @DeleteMapping("/{postId}/schedule")
    public ApiResponse<Void> cancelSchedule(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.cancelSchedule(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "删除文章", description = "删除指定文章（软删除）")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.deletePost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/restore")
    public ApiResponse<Void> restorePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.restorePost(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "保存草稿", description = "保存文章草稿，支持自动保存和手动保存")
    @PostMapping("/{postId}/draft")
    public ApiResponse<Void> saveDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody SaveDraftRequest request) {
        Long userId = UserContext.requireUserId();
        postCommandService.saveDraft(userId, postId, new SaveDraftCommand(
                request.getContent(),
                request.getContentType(),
                request.getIsAutoSave(),
                request.getDeviceId()
        ));
        return ApiResponse.success();
    }

    @DeleteMapping("/{postId}/draft")
    public ApiResponse<Void> deleteDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postCommandService.deleteDraft(userId, postId);
        return ApiResponse.success();
    }

    @Operation(
            summary = "为文章添加标签",
            description = "为指定文章添加标签，会替换现有标签。支持同时添加多个标签（最多10个），不存在的标签会自动创建。"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功添加标签"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误（标签数量超过限制、标签名称不合法等）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（只有文章作者可以修改标签）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文章不存在")
    })
    @PostMapping("/{postId}/tags")
    public ApiResponse<Void> attachTags(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "添加标签请求", required = true)
            @Valid @RequestBody AttachTagsRequest request) {
        Long userId = UserContext.requireUserId();
        postCommandService.attachTags(userId, postId, request.getTags());
        return ApiResponse.success();
    }

    @Operation(summary = "移除文章的标签", description = "移除文章的指定标签。根据 slug 移除，如果标签不存在则忽略。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功移除标签"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（只有文章作者可以移除标签）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文章不存在")
    })
    @DeleteMapping("/{postId}/tags/{slug}")
    public ApiResponse<Void> detachTag(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "标签 slug（URL 友好标识）", required = true, example = "spring-boot")
            @PathVariable String slug) {
        Long userId = UserContext.requireUserId();
        postCommandService.detachTag(userId, postId, slug);
        return ApiResponse.success();
    }
}
