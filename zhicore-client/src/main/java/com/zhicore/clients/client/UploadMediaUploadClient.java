package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传服务媒体上传契约。
 */
public interface UploadMediaUploadClient {

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResponse> uploadImage(@RequestPart("file") MultipartFile file);

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResponse> uploadAudio(@RequestPart("file") MultipartFile file);

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
