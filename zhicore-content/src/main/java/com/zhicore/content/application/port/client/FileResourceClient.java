package com.zhicore.content.application.port.client;

import java.util.Optional;

/**
 * 文件资源客户端端口。
 *
 * 用于隔离文章域对文件服务的读取与删除依赖。
 */
public interface FileResourceClient {

    Optional<String> getFileUrl(String fileId);

    DeleteResult deleteFile(String fileId);

    record DeleteResult(boolean success, String message) {

        public static DeleteResult ok() {
            return new DeleteResult(true, null);
        }

        public static DeleteResult fail(String message) {
            return new DeleteResult(false, message);
        }
    }
}
