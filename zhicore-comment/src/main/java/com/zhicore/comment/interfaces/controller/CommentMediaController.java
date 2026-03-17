package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.service.command.CommentMediaCommandService;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评论媒体管理控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "评论媒体管理", description = "评论图片和语音上传管理")
@RestController
@RequestMapping("/api/v1/comments/media")
@RequiredArgsConstructor
public class CommentMediaController {

    private final CommentMediaCommandService commentMediaCommandService;

    /**
     * 上传评论图片
     *
     * @param file 图片文件
     * @return 图片文件ID（UUIDv7格式）
     */
    @Operation(
            summary = "上传评论图片",
            description = "上传评论图片，支持常见图片格式（JPG、PNG等），最多9张"
    )
    @PostMapping("/images")
    public ApiResponse<String> uploadImage(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(commentMediaCommandService.uploadImage(file));
    }

    /**
     * 上传评论语音
     *
     * @param file 语音文件
     * @return 语音文件ID（UUIDv7格式）
     */
    @Operation(
            summary = "上传评论语音",
            description = "上传评论语音，支持常见音频格式（MP3、AAC、M4A等），最长60秒"
    )
    @PostMapping("/voice")
    public ApiResponse<String> uploadVoice(
            @Parameter(description = "语音文件", required = true)
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(commentMediaCommandService.uploadVoice(file));
    }
}
