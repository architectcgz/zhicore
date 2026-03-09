package com.zhicore.upload.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文件上传服务 Sentinel 配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "upload.sentinel")
public class UploadSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int uploadImageQps = 60;

    @Min(1)
    private int uploadAudioQps = 40;

    @Min(1)
    private int uploadImagesBatchQps = 20;

    @Min(1)
    private int getFileUrlQps = 200;

    @Min(1)
    private int deleteFileQps = 60;

    @Min(0)
    private int warmUpPeriodSec = 5;
}
