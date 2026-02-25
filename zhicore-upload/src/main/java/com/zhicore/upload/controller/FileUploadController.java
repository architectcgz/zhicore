package com.zhicore.upload.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.upload.model.FileUploadResponse;
import com.zhicore.upload.service.FileUploadService;
import com.platform.fileservice.client.model.AccessLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传控制器
 * 
 * 提供统一的文件上传接口，包括图片上传、批量上传、文件删除等功能
 * 
 * @author ZhiCore Team
 */
@Tag(name = "文件上传管理", description = "文件上传服务，提供图片上传、批量上传、文件删除等功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 上传图片（默认公开访问）
     * 
     * @param file 图片文件
     * @return 文件上传响应
     */
    @Operation(
            summary = "上传图片",
            description = "上传图片文件，默认公开访问。支持的格式：JPEG、PNG、GIF、WebP，最大50MB"
    )
    @PostMapping("/image")
    public ApiResponse<FileUploadResponse> uploadImage(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        log.info("上传图片: filename={}, size={}, contentType={}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType());

        FileUploadResponse response = fileUploadService.uploadImage(file, AccessLevel.PUBLIC);

        log.info("图片上传成功: fileId={}, url={}, instantUpload={}", 
            response.getFileId(), response.getUrl(), response.getInstantUpload());
        
        return ApiResponse.success(response);
    }

    /**
     * 上传音频（默认公开访问）
     * 
     * @param file 音频文件
     * @return 文件上传响应
     */
    @Operation(
            summary = "上传音频",
            description = "上传音频文件，默认公开访问。支持的格式：MP3、AAC、M4A、WAV，最大100MB"
    )
    @PostMapping("/audio")
    public ApiResponse<FileUploadResponse> uploadAudio(
            @Parameter(description = "音频文件", required = true)
            @RequestParam("file") MultipartFile file) {
        log.info("上传音频: filename={}, size={}, contentType={}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType());

        FileUploadResponse response = fileUploadService.uploadAudio(file, AccessLevel.PUBLIC);

        log.info("音频上传成功: fileId={}, url={}, instantUpload={}", 
            response.getFileId(), response.getUrl(), response.getInstantUpload());
        
        return ApiResponse.success(response);
    }

    /**
     * 上传图片（指定访问级别）
     * 
     * @param file 图片文件
     * @param accessLevel 访问级别（PUBLIC/PRIVATE）
     * @return 文件上传响应
     */
    @Operation(
            summary = "上传图片（指定访问级别）",
            description = "上传图片文件并指定访问级别。PUBLIC=公开访问，PRIVATE=私有访问（需要签名URL）"
    )
    @PostMapping("/image/with-access")
    public ApiResponse<FileUploadResponse> uploadImageWithAccess(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "访问级别", required = true, example = "PUBLIC")
            @RequestParam("accessLevel") AccessLevel accessLevel) {
        log.info("上传图片（指定访问级别）: filename={}, size={}, contentType={}, accessLevel={}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType(), accessLevel);

        FileUploadResponse response = fileUploadService.uploadImage(file, accessLevel);

        log.info("图片上传成功: fileId={}, url={}, accessLevel={}, instantUpload={}", 
            response.getFileId(), response.getUrl(), response.getAccessLevel(), response.getInstantUpload());
        
        return ApiResponse.success(response);
    }

    /**
     * 批量上传图片
     * 
     * @param files 图片文件列表
     * @param accessLevel 访问级别（可选，默认PUBLIC）
     * @return 文件上传响应列表
     */
    @Operation(
            summary = "批量上传图片",
            description = "批量上传多个图片文件。如果某个文件上传失败，会返回部分成功的结果"
    )
    @PostMapping("/images/batch")
    public ApiResponse<List<FileUploadResponse>> uploadImagesBatch(
            @Parameter(description = "图片文件列表", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "访问级别（可选，默认PUBLIC）", example = "PUBLIC")
            @RequestParam(value = "accessLevel", required = false, defaultValue = "PUBLIC") AccessLevel accessLevel) {
        log.info("批量上传图片: fileCount={}, accessLevel={}", files.size(), accessLevel);

        List<FileUploadResponse> responses = fileUploadService.uploadImagesBatch(files, accessLevel);

        long successCount = responses.stream().filter(r -> r.getFileId() != null).count();
        log.info("批量上传完成: total={}, success={}, failed={}", 
            files.size(), successCount, files.size() - successCount);
        
        return ApiResponse.success(responses);
    }

    /**
     * 获取文件访问URL
     * 
     * @param fileId 文件ID
     * @return 文件访问URL
     */
    @Operation(
            summary = "获取文件访问URL",
            description = "根据文件ID获取文件的访问URL，供其他微服务调用"
    )
    @GetMapping("/file/{fileId}/url")
    public ApiResponse<String> getFileUrl(
            @Parameter(description = "文件ID", required = true, example = "01JGXXX-XXX-XXX-XXX-XXXXXXXXXXXX")
            @PathVariable String fileId) {
        log.debug("获取文件URL: fileId={}", fileId);

        String url = fileUploadService.getFileUrl(fileId);

        log.debug("获取文件URL成功: fileId={}, url={}", fileId, url);
        return ApiResponse.success(url);
    }

    /**
     * 删除文件
     * 
     * @param fileId 文件ID
     * @return 操作结果
     */
    @Operation(
            summary = "删除文件",
            description = "根据文件ID删除文件"
    )
    @DeleteMapping("/file/{fileId}")
    public ApiResponse<Void> deleteFile(
            @Parameter(description = "文件ID", required = true, example = "file_123456789")
            @PathVariable String fileId) {
        log.info("删除文件: fileId={}", fileId);

        fileUploadService.deleteFile(fileId);

        log.info("文件删除成功: fileId={}", fileId);
        return ApiResponse.success();
    }
}
