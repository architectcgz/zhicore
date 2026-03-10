package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 上传服务文件删除契约。
 */
public interface UploadFileDeleteClient {

    @DeleteMapping("/file/{fileId}")
    ApiResponse<Void> deleteFile(@PathVariable("fileId") String fileId);
}
