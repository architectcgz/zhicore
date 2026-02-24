package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传服务 Feign 客户端
 *
 * 用于获取文件 URL、删除文件和上传文件。
 * 注意：fallbackFactory 建议由各服务自行通过 @FeignClient 配置指定，以便按业务差异进行降级处理。
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload")
public interface UploadServiceClient {

    /**
     * 获取文件访问 URL
     *
     * @param fileId 文件ID（UUIDv7 格式）
     * @return 文件访问URL
     */
    @GetMapping("/file/{fileId}/url")
    ApiResponse<String> getFileUrl(@PathVariable("fileId") String fileId);

    /**
     * 删除文件
     *
     * @param fileId 文件ID（UUIDv7 格式）
     * @return 操作结果
     */
    @DeleteMapping("/file/{fileId}")
    ApiResponse<Void> deleteFile(@PathVariable("fileId") String fileId);

    /**
     * 上传图片（默认公开访问）
     *
     * @param file 图片文件
     * @return 文件上传响应（包含 fileId 和 url）
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResponse> uploadImage(@RequestPart("file") MultipartFile file);

    /**
     * 上传音频（默认公开访问）
     *
     * @param file 音频文件
     * @return 文件上传响应（包含 fileId 和 url）
     */
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResponse> uploadAudio(@RequestPart("file") MultipartFile file);

    /**
     * 文件上传响应
     */
    class FileUploadResponse {
        private String fileId;
        private String url;
        private Boolean instantUpload;
        private String accessLevel;

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Boolean getInstantUpload() {
            return instantUpload;
        }

        public void setInstantUpload(Boolean instantUpload) {
            this.instantUpload = instantUpload;
        }

        public String getAccessLevel() {
            return accessLevel;
        }

        public void setAccessLevel(String accessLevel) {
            this.accessLevel = accessLevel;
        }
    }
}
