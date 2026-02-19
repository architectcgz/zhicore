package com.blog.upload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置属性
 * 
 * 从 application.yml 中读取 file-upload 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-upload")
public class FileUploadProperties {

    /**
     * 最大文件大小（字节）
     */
    private Long maxFileSize = 52428800L; // 50MB

    /**
     * 允许的图片类型
     */
    private String[] allowedImageTypes = {
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp"
    };

    /**
     * 最大音频文件大小（字节）
     */
    private Long maxAudioSize = 10485760L; // 10MB

    /**
     * 允许的音频类型
     */
    private String[] allowedAudioTypes = {
        "audio/mpeg",      // MP3
        "audio/mp3",       // MP3
        "audio/mp4",       // M4A
        "audio/x-m4a",     // M4A
        "audio/aac",       // AAC
        "audio/wav",       // WAV
        "audio/x-wav",     // WAV
        "audio/ogg",       // OGG
        "audio/webm"       // WebM Audio
    };
}
