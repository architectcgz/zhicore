package com.blog.upload.service.impl;

import com.blog.upload.config.FileUploadProperties;
import com.blog.upload.exception.ErrorCodes;
import com.blog.upload.exception.FileValidationException;
import com.blog.upload.exception.FileSizeExceededException;
import com.blog.upload.exception.InvalidFileTypeException;
import com.blog.upload.service.FileValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * 文件验证服务实现类
 * 
 * 实现博客系统特定的文件验证逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileValidationServiceImpl implements FileValidationService {

    private final FileUploadProperties fileUploadProperties;

    /**
     * 验证图片文件
     * 
     * 验证文件非空、类型和大小是否符合要求
     * 
     * @param file 文件对象
     * @throws FileValidationException 如果验证失败
     */
    @Override
    public void validateImageFile(MultipartFile file) {
        log.debug("开始验证图片文件: {}", file.getOriginalFilename());

        // 验证文件非空
        if (file == null || file.isEmpty()) {
            log.warn("文件验证失败: 文件为空");
            throw new FileValidationException(ErrorCodes.FILE_EMPTY, "文件不能为空");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        validateContentType(contentType, fileUploadProperties.getAllowedImageTypes());

        // 验证文件大小
        long fileSize = file.getSize();
        validateFileSize(fileSize, fileUploadProperties.getMaxFileSize());

        log.debug("图片文件验证通过: {}, 类型: {}, 大小: {} 字节", 
            file.getOriginalFilename(), contentType, fileSize);
    }

    /**
     * 验证音频文件
     * 
     * 验证文件非空、类型和大小是否符合要求
     * 
     * @param file 文件对象
     * @throws FileValidationException 如果验证失败
     */
    @Override
    public void validateAudioFile(MultipartFile file) {
        log.debug("开始验证音频文件: {}", file.getOriginalFilename());

        // 验证文件非空
        if (file == null || file.isEmpty()) {
            log.warn("文件验证失败: 文件为空");
            throw new FileValidationException(ErrorCodes.FILE_EMPTY, "文件不能为空");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        validateContentType(contentType, fileUploadProperties.getAllowedAudioTypes());

        // 验证文件大小
        long fileSize = file.getSize();
        validateFileSize(fileSize, fileUploadProperties.getMaxAudioSize());

        log.debug("音频文件验证通过: {}, 类型: {}, 大小: {} 字节", 
            file.getOriginalFilename(), contentType, fileSize);
    }

    /**
     * 验证文件大小
     * 
     * 检查文件大小是否超过限制
     * 
     * @param fileSize 文件大小（字节）
     * @param maxSize 最大允许大小（字节）
     * @throws FileSizeExceededException 如果文件过大
     */
    @Override
    public void validateFileSize(long fileSize, long maxSize) {
        if (fileSize > maxSize) {
            log.warn("文件大小验证失败: 文件大小 {} 字节超过限制 {} 字节", fileSize, maxSize);
            throw new FileSizeExceededException(fileSize, maxSize);
        }
        log.debug("文件大小验证通过: {} 字节 (限制: {} 字节)", fileSize, maxSize);
    }

    /**
     * 验证文件类型
     * 
     * 检查 MIME 类型是否在白名单中
     * 
     * @param contentType MIME 类型
     * @param allowedTypes 允许的类型列表
     * @throws InvalidFileTypeException 如果类型不允许
     */
    @Override
    public void validateContentType(String contentType, String[] allowedTypes) {
        if (contentType == null || contentType.trim().isEmpty()) {
            log.warn("文件类型验证失败: Content-Type 为空");
            throw new InvalidFileTypeException("文件类型不能为空");
        }

        // 检查是否在允许的类型列表中
        boolean isAllowed = Arrays.stream(allowedTypes)
            .anyMatch(allowedType -> allowedType.equalsIgnoreCase(contentType.trim()));

        if (!isAllowed) {
            log.warn("文件类型验证失败: {} 不在允许的类型列表中", contentType);
            throw new InvalidFileTypeException(contentType, allowedTypes);
        }

        log.debug("文件类型验证通过: {}", contentType);
    }
}
