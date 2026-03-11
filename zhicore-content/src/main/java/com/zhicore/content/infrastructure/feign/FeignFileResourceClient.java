package com.zhicore.content.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.port.client.FileResourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于上传服务的文件资源客户端适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignFileResourceClient implements FileResourceClient {

    private final ContentUploadServiceClient uploadServiceClient;

    @Override
    public Optional<String> getFileUrl(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }

        try {
            ApiResponse<String> response = uploadServiceClient.getFileUrl(fileId);
            if (response != null && response.isSuccess() && response.getData() != null && !response.getData().isBlank()) {
                return Optional.of(response.getData());
            }
            log.warn("获取文件 URL 失败: fileId={}, response={}", fileId, response);
        } catch (Exception e) {
            log.error("调用上传服务获取文件 URL 异常: fileId={}", fileId, e);
        }
        return Optional.empty();
    }

    @Override
    public DeleteResult deleteFile(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return DeleteResult.ok();
        }

        try {
            ApiResponse<Void> response = uploadServiceClient.deleteFile(fileId);
            if (response != null && response.isSuccess()) {
                return DeleteResult.ok();
            }

            String message = response != null ? response.getMessage() : "null response";
            log.warn("删除文件失败: fileId={}, message={}", fileId, message);
            return DeleteResult.fail(message);
        } catch (Exception e) {
            log.error("调用上传服务删除文件异常: fileId={}", fileId, e);
            return DeleteResult.fail(e.getMessage());
        }
    }
}
