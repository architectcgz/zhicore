package com.zhicore.user.infrastructure.feign;

import com.zhicore.api.client.UploadFileClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * zhicore-upload 服务 Feign 客户端
 * 用于获取文件 URL 和删除文件
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload", fallbackFactory = UploadServiceFallbackFactory.class)
public interface ZhiCoreUploadClient extends UploadFileClient {
}
