package com.zhicore.upload.exception;

/**
 * 文件类型无效异常
 * 
 * 当上传的文件类型不在允许的类型列表中时抛出
 */
public class InvalidFileTypeException extends FileValidationException {

    public InvalidFileTypeException(String message) {
        super(ErrorCodes.INVALID_FILE_TYPE, message);
    }

    public InvalidFileTypeException(String contentType, String[] allowedTypes) {
        super(ErrorCodes.INVALID_FILE_TYPE, String.format(
            "不支持的文件类型: %s。允许的类型: %s",
            contentType,
            String.join(", ", allowedTypes)
        ));
    }
}
