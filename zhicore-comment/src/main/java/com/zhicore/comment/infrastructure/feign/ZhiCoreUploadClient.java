package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.client.UploadMediaClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * ZhiCore Upload 服务 Feign 客户端
 * 
 * 调用 zhicore-upload 服务的文件上传和删除接口
 * 
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload", fallbackFactory = UploadServiceFallbackFactory.class)
public interface ZhiCoreUploadClient extends UploadMediaClient {
}
