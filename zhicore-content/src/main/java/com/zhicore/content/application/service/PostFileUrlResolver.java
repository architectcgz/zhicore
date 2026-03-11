package com.zhicore.content.application.service;

import com.zhicore.api.client.UploadFileClient;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文章相关文件 URL 解析器。
 *
 * 收口封面图、头像等文件地址查询逻辑，
 * 避免多个 query service 反复依赖上传服务细节。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostFileUrlResolver {

    private final UploadFileClient uploadFileClient;

    public String resolve(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return null;
        }

        try {
            ApiResponse<String> response = uploadFileClient.getFileUrl(fileId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
            log.warn("Failed to get file URL from upload service: fileId={}, response={}", fileId, response);
        } catch (Exception e) {
            log.error("Failed to get file URL: fileId={}", fileId, e);
        }
        return null;
    }
}
