package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传服务媒体上传/删除复合契约。
 */
public interface UploadMediaClient extends UploadFileDeleteClient {

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<UploadMediaUploadClient.FileUploadResponse> uploadImage(@RequestPart("file") MultipartFile file);

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<UploadMediaUploadClient.FileUploadResponse> uploadAudio(@RequestPart("file") MultipartFile file);
}
