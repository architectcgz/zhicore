package com.zhicore.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 上传服务 Feign 客户端
 *
 * 用于获取文件 URL、删除文件和上传文件。
 * 注意：fallbackFactory 建议由各服务自行通过 @FeignClient 配置指定，以便按业务差异进行降级处理。
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload")
public interface UploadServiceClient extends UploadMediaClient {

    @GetMapping("/file/{fileId}/url")
    com.zhicore.common.result.ApiResponse<String> getFileUrl(@PathVariable("fileId") String fileId);
}
