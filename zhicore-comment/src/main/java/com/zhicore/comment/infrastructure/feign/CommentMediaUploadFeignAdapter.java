package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.client.UploadMediaUploadClient.FileUploadResponse;
import com.zhicore.comment.application.port.CommentMediaUploadPort;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评论媒体上传 Feign 适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentMediaUploadFeignAdapter implements CommentMediaUploadPort {

    private final ZhiCoreUploadClient zhiCoreUploadClient;

    @Override
    public String uploadImage(MultipartFile file) {
        return extractFileId(zhiCoreUploadClient.uploadImage(file), "图片");
    }

    @Override
    public String uploadVoice(MultipartFile file) {
        return extractFileId(zhiCoreUploadClient.uploadAudio(file), "语音");
    }

    private String extractFileId(ApiResponse<FileUploadResponse> response, String mediaType) {
        if (response == null) {
            log.error("{}上传失败: response is null", mediaType);
            throw new BusinessException("上传服务无响应");
        }
        if (!response.isSuccess() || response.getData() == null) {
            log.error("{}上传失败: code={}, message={}", mediaType, response.getCode(), response.getMessage());
            throw new BusinessException(response.getCode(), response.getMessage());
        }
        return response.getData().getFileId();
    }
}
