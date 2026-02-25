package com.zhicore.upload.exception;

/**
 * 文件大小超限异常
 * 
 * 当上传的文件大小超过配置的最大限制时抛出
 */
public class FileSizeExceededException extends FileValidationException {

    public FileSizeExceededException(String message) {
        super(ErrorCodes.FILE_SIZE_EXCEEDED, message);
    }

    public FileSizeExceededException(long fileSize, long maxSize) {
        super(ErrorCodes.FILE_SIZE_EXCEEDED, String.format(
            "文件大小超过限制: %d 字节。最大允许: %d 字节 (%.2f MB)",
            fileSize,
            maxSize,
            maxSize / 1024.0 / 1024.0
        ));
    }
}
