package com.zhicore.upload.service.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.platform.fileservice.client.model.AccessLevel;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.upload.model.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传服务 Sentinel block 处理器。
 */
public final class UploadSentinelHandlers {

    private UploadSentinelHandlers() {
    }

    public static FileUploadResponse handleUploadImageBlocked(MultipartFile file, AccessLevel accessLevel,
                                                              BlockException ex) {
        throw tooManyRequests("图片上传请求过于频繁，请稍后重试");
    }

    public static FileUploadResponse handleUploadAudioBlocked(MultipartFile file, AccessLevel accessLevel,
                                                              BlockException ex) {
        throw tooManyRequests("音频上传请求过于频繁，请稍后重试");
    }

    public static List<FileUploadResponse> handleUploadImagesBatchBlocked(List<MultipartFile> files,
                                                                          AccessLevel accessLevel,
                                                                          BlockException ex) {
        throw tooManyRequests("批量图片上传请求过于频繁，请稍后重试");
    }

    public static String handleGetFileUrlBlocked(String fileId, BlockException ex) {
        throw tooManyRequests("文件地址查询过于频繁，请稍后重试");
    }

    public static void handleDeleteFileBlocked(String fileId, BlockException ex) {
        throw tooManyRequests("文件删除请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
