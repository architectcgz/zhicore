package com.zhicore.upload.service;

import com.zhicore.upload.exception.FileSizeExceededException;
import com.zhicore.upload.exception.FileValidationException;
import com.zhicore.upload.exception.InvalidFileTypeException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件验证服务接口
 * 
 * 提供博客系统特定的文件验证规则，包括文件类型、大小等验证
 */
public interface FileValidationService {

    /**
     * 验证图片文件
     * 
     * 验证文件非空、类型和大小是否符合要求
     * 
     * @param file 文件对象
     * @throws FileValidationException 如果验证失败
     */
    void validateImageFile(MultipartFile file);

    /**
     * 验证音频文件
     * 
     * 验证文件非空、类型和大小是否符合要求
     * 
     * @param file 文件对象
     * @throws FileValidationException 如果验证失败
     */
    void validateAudioFile(MultipartFile file);

    /**
     * 验证文件大小
     * 
     * 检查文件大小是否超过限制
     * 
     * @param fileSize 文件大小（字节）
     * @param maxSize 最大允许大小（字节）
     * @throws FileSizeExceededException 如果文件过大
     */
    void validateFileSize(long fileSize, long maxSize);

    /**
     * 验证文件类型
     * 
     * 检查 MIME 类型是否在白名单中
     * 
     * @param contentType MIME 类型
     * @param allowedTypes 允许的类型列表
     * @throws InvalidFileTypeException 如果类型不允许
     */
    void validateContentType(String contentType, String[] allowedTypes);
}
