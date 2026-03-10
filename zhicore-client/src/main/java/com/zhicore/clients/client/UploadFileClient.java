package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 上传服务文件查询/删除复合契约。
 */
public interface UploadFileClient extends UploadFileDeleteClient {

    @GetMapping("/file/{fileId}/url")
    ApiResponse<String> getFileUrl(@PathVariable("fileId") String fileId);
}
