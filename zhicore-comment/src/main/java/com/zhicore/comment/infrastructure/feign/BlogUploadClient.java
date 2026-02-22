package com.zhicore.comment.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * ZhiCore Upload 服务 Feign 客户端
 * 
 * 调用 zhicore-upload 服务的文件上传和删除接口
 * 
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-upload", path = "/api/v1/upload")
public interface ZhiCoreUploadClient {

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
     * 删除文件
     * 
     * @param fileId 文件ID
     * @return 操作结果
     */
    @DeleteMapping("/file/{fileId}")
    ApiResponse<Void> deleteFile(@PathVariable("fileId") String fileId);

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
