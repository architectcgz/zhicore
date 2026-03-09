package com.zhicore.content.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * content 服务专用上传客户端。
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload", fallbackFactory = ContentUploadServiceFallbackFactory.class)
public interface ContentUploadServiceClient {

    @GetMapping("/file/{fileId}/url")
    ApiResponse<String> getFileUrl(@PathVariable("fileId") String fileId);

    @DeleteMapping("/file/{fileId}")
    ApiResponse<Void> deleteFile(@PathVariable("fileId") String fileId);
}
