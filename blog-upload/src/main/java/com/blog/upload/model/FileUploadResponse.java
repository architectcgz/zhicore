package com.blog.upload.model;

import com.platform.fileservice.client.model.AccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件上传响应模型
 * 
 * 包含文件上传后的元数据信息，包括文件ID、访问URL、大小、哈希值等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传响应")
public class FileUploadResponse {

    /**
     * 文件ID
     */
    @Schema(description = "文件唯一标识符", example = "file_123456789")
    private String fileId;

    /**
     * 文件访问URL
     */
    @Schema(description = "文件访问URL", example = "https://cdn.example.com/files/abc123.jpg")
    private String url;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）", example = "1048576")
    private Long fileSize;

    /**
     * 文件哈希值（由 file-service 返回）
     */
    @Schema(description = "文件SHA-256哈希值", example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    private String fileHash;

    /**
     * 是否秒传（由 file-service 返回）
     */
    @Schema(description = "是否通过秒传上传", example = "false")
    private Boolean instantUpload;

    /**
     * 上传时间
     */
    @Schema(description = "文件上传时间", example = "2026-02-08T00:00:00")
    private LocalDateTime uploadTime;

    /**
     * 文件访问级别
     */
    @Schema(description = "文件访问级别", example = "PUBLIC")
    private AccessLevel accessLevel;

    /**
     * 原始文件名
     */
    @Schema(description = "原始文件名", example = "avatar.jpg")
    private String originalName;

    /**
     * 文件内容类型（MIME类型）
     */
    @Schema(description = "文件MIME类型", example = "image/jpeg")
    private String contentType;
}
