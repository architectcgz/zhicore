package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.infrastructure.feign.ZhiCoreUploadClient;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评论媒体管理控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "评论媒体管理", description = "评论图片和语音上传管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/comments/media")
@RequiredArgsConstructor
public class CommentMediaController {

    private final ZhiCoreUploadClient ZhiCoreUploadClient;

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
        log.info("上传评论图片: filename={}, size={}", 
            file.getOriginalFilename(), file.getSize());

        // 调用 ZhiCore-upload 服务上传图片
        ApiResponse<ZhiCoreUploadClient.FileUploadResponse> response = ZhiCoreUploadClient.uploadImage(file);
        
        if (!response.isSuccess() || response.getData() == null) {
            log.error("图片上传失败: {}", response.getMessage());
            return ApiResponse.fail(response.getCode(), response.getMessage());
        }

        String fileId = response.getData().getFileId();
        log.info("评论图片上传成功: fileId={}", fileId);
        return ApiResponse.success(fileId);
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
        log.info("上传评论语音: filename={}, size={}", 
            file.getOriginalFilename(), file.getSize());

        // 调用 ZhiCore-upload 服务上传音频
        ApiResponse<ZhiCoreUploadClient.FileUploadResponse> response = ZhiCoreUploadClient.uploadAudio(file);
        
        if (!response.isSuccess() || response.getData() == null) {
            log.error("语音上传失败: {}", response.getMessage());
            return ApiResponse.fail(response.getCode(), response.getMessage());
        }

        String fileId = response.getData().getFileId();
        log.info("评论语音上传成功: fileId={}", fileId);
        return ApiResponse.success(fileId);
    }
}
