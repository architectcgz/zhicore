package com.zhicore.user.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * zhicore-upload 服务 Feign 客户端
 * 用于获取文件 URL 和删除文件
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload", fallbackFactory = UploadServiceFallbackFactory.class)
public interface ZhiCoreUploadClient {

    /**
     * 获取文件访问 URL
     *
     * @param fileId 文件ID（UUIDv7格式）
     * @return 文件访问URL
     */
    @GetMapping("/file/{fileId}/url")
    ApiResponse<String> getFileUrl(@PathVariable("fileId") String fileId);

    /**
     * 删除文件
     *
     * @param fileId 文件ID（UUIDv7格式）
     * @return 操作结果
     */
    @DeleteMapping("/file/{fileId}")
    ApiResponse<Void> deleteFile(@PathVariable("fileId") String fileId);
}
