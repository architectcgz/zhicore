package com.zhicore.content.infrastructure.feign;

import com.zhicore.api.client.UploadFileClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * content 服务专用上传客户端。
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload", fallbackFactory = ContentUploadServiceFallbackFactory.class)
public interface ContentUploadServiceClient extends UploadFileClient {
}
