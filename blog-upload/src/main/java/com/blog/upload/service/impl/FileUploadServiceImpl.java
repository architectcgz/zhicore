package com.blog.upload.service.impl;

import com.blog.upload.exception.ErrorCodes;
import com.blog.upload.exception.FileUploadException;
import com.blog.upload.model.FileUploadResponse;
import com.blog.upload.service.FileUploadService;
import com.blog.upload.service.FileValidationService;
import com.platform.fileservice.client.FileServiceClient;
import com.platform.fileservice.client.exception.FileServiceException;
import com.platform.fileservice.client.model.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传服务实现类
 * 
 * 核心业务逻辑服务，主要负责文件验证和调用 file-service-client
 * 
 * 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final FileServiceClient fileServiceClient;
    private final FileValidationService fileValidationService;

    /**
     * 上传图片文件
     * 
     * 流程：验证文件 → 转换为 File → 调用 file-service-client → 转换响应
     * 
     * @param file 文件对象
     * @param accessLevel 访问级别
     * @return 文件上传响应（包含 fileId 和 URL）
     */
    @Override
    public FileUploadResponse uploadImage(MultipartFile file, AccessLevel accessLevel) {
        String originalFilename = file.getOriginalFilename();
        log.info("开始上传图片: {}, 大小: {} 字节, 访问级别: {}", 
            originalFilename, file.getSize(), accessLevel);

        try {
            // 1. 验证文件
            fileValidationService.validateImageFile(file);
            log.debug("文件验证通过: {}", originalFilename);

            // 2. 转换 MultipartFile 为 File
            File tempFile = convertMultipartFileToFile(file);
            log.debug("临时文件创建成功: {}", tempFile.getAbsolutePath());

            try {
                // 3. 调用 file-service-client 上传文件
                // 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
                com.platform.fileservice.client.model.FileUploadResponse clientResponse = 
                    fileServiceClient.uploadFile(tempFile, accessLevel);
                
                log.info("文件上传成功: fileId={}, url={}, 原始文件名={}", 
                    clientResponse.getFileId(), clientResponse.getUrl(), originalFilename);

                // 4. 转换响应
                FileUploadResponse response = convertToFileUploadResponse(clientResponse, file);
                
                // 检查是否为秒传（通过比较文件大小和上传时间来推断）
                // 注意：file-service-client 可能不直接返回 instantUpload 标志
                // 这里我们通过日志记录来标识秒传情况
                if (clientResponse.getFileId() != null) {
                    log.debug("文件上传完成: fileId={}, 秒传={}", 
                        clientResponse.getFileId(), "未知");
                }

                return response;

            } finally {
                // 5. 清理临时文件
                deleteTempFile(tempFile);
            }

        } catch (FileServiceException e) {
            log.error("文件服务调用失败: {}, 原因: {}", originalFilename, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.UPLOAD_FAILED, 
                "文件上传失败: " + e.getMessage(), 
                e
            );
        } catch (IOException e) {
            log.error("文件处理失败: {}, 原因: {}", originalFilename, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.UPLOAD_FAILED, 
                "文件处理失败: " + e.getMessage(), 
                e
            );
        }
    }

    /**
     * 批量上传图片
     * 
     * 循环调用 uploadImage，收集结果
     * 
     * @param files 文件列表
     * @param accessLevel 访问级别
     * @return 文件上传响应列表
     */
    @Override
    public List<FileUploadResponse> uploadImagesBatch(List<MultipartFile> files, AccessLevel accessLevel) {
        log.info("开始批量上传图片: 文件数量={}, 访问级别={}", files.size(), accessLevel);

        List<FileUploadResponse> responses = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            try {
                FileUploadResponse response = uploadImage(file, accessLevel);
                responses.add(response);
                successCount++;
                log.debug("批量上传进度: {}/{}, 当前文件: {} - 成功", 
                    i + 1, files.size(), file.getOriginalFilename());
            } catch (Exception e) {
                failureCount++;
                log.error("批量上传进度: {}/{}, 当前文件: {} - 失败: {}", 
                    i + 1, files.size(), file.getOriginalFilename(), e.getMessage());
                // 继续处理下一个文件，不中断整个批量上传
                // 可以选择将失败信息也添加到响应中，或者抛出异常
                // 这里选择记录日志并继续
            }
        }

        log.info("批量上传完成: 总数={}, 成功={}, 失败={}", 
            files.size(), successCount, failureCount);

        return responses;
    }

    /**
     * 上传音频文件
     * 
     * 流程：验证文件 → 转换为 File → 调用 file-service-client → 转换响应
     * 
     * @param file 文件对象
     * @param accessLevel 访问级别
     * @return 文件上传响应（包含 fileId 和 URL）
     */
    @Override
    public FileUploadResponse uploadAudio(MultipartFile file, AccessLevel accessLevel) {
        String originalFilename = file.getOriginalFilename();
        log.info("开始上传音频: {}, 大小: {} 字节, 访问级别: {}", 
            originalFilename, file.getSize(), accessLevel);

        try {
            // 1. 验证文件
            fileValidationService.validateAudioFile(file);
            log.debug("文件验证通过: {}", originalFilename);

            // 2. 转换 MultipartFile 为 File
            File tempFile = convertMultipartFileToFile(file);
            log.debug("临时文件创建成功: {}", tempFile.getAbsolutePath());

            try {
                // 3. 调用 file-service-client 上传文件
                // 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
                com.platform.fileservice.client.model.FileUploadResponse clientResponse = 
                    fileServiceClient.uploadFile(tempFile, accessLevel);
                
                log.info("音频上传成功: fileId={}, url={}, 原始文件名={}", 
                    clientResponse.getFileId(), clientResponse.getUrl(), originalFilename);

                // 4. 转换响应
                FileUploadResponse response = convertToFileUploadResponse(clientResponse, file);
                
                return response;

            } finally {
                // 5. 清理临时文件
                deleteTempFile(tempFile);
            }

        } catch (FileServiceException e) {
            log.error("文件服务调用失败: {}, 原因: {}", originalFilename, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.UPLOAD_FAILED, 
                "音频上传失败: " + e.getMessage(), 
                e
            );
        } catch (IOException e) {
            log.error("文件处理失败: {}, 原因: {}", originalFilename, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.UPLOAD_FAILED, 
                "文件处理失败: " + e.getMessage(), 
                e
            );
        }
    }

    /**
     * 获取文件访问URL
     * 
     * 直接调用 file-service-client.getFileUrl
     * 缓存逻辑由 file-service 内部实现
     * 
     * @param fileId 文件ID
     * @return 文件访问URL
     */
    @Override
    public String getFileUrl(String fileId) {
        log.debug("开始获取文件URL: fileId={}", fileId);

        try {
            String url = fileServiceClient.getFileUrl(fileId);
            log.debug("获取文件URL成功: fileId={}, url={}", fileId, url);
            return url;
        } catch (FileServiceException e) {
            log.error("获取文件URL失败: fileId={}, 原因: {}", fileId, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.FILE_NOT_FOUND,
                "获取文件URL失败: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 删除文件
     * 
     * 直接调用 file-service-client.deleteFile
     * 缓存清除逻辑由 file-service 内部实现
     * 
     * @param fileId 文件ID
     */
    @Override
    public void deleteFile(String fileId) {
        log.info("开始删除文件: fileId={}", fileId);

        try {
            fileServiceClient.deleteFile(fileId);
            log.info("文件删除成功: fileId={}", fileId);
        } catch (FileServiceException e) {
            log.error("文件删除失败: fileId={}, 原因: {}", fileId, e.getMessage(), e);
            throw new FileUploadException(
                ErrorCodes.DELETE_FAILED,
                "文件删除失败: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 转换 MultipartFile 为 File
     * 
     * 创建临时文件用于上传
     * 
     * @param multipartFile MultipartFile 对象
     * @return File 对象
     * @throws IOException 如果转换失败
     */
    private File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        String prefix = "upload_";
        String suffix = "";
        
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 创建临时文件
        Path tempFile = Files.createTempFile(prefix, suffix);
        multipartFile.transferTo(tempFile.toFile());
        
        log.debug("MultipartFile 转换为 File: {} -> {}", 
            originalFilename, tempFile.toAbsolutePath());
        
        return tempFile.toFile();
    }

    /**
     * 转换 file-service-client 响应为 blog-upload 响应
     * 
     * @param clientResponse file-service-client 响应
     * @param originalFile 原始文件
     * @return blog-upload 响应
     */
    private FileUploadResponse convertToFileUploadResponse(
            com.platform.fileservice.client.model.FileUploadResponse clientResponse,
            MultipartFile originalFile) {
        
        return FileUploadResponse.builder()
            .fileId(clientResponse.getFileId())
            .url(clientResponse.getUrl())
            .fileSize(clientResponse.getFileSize())
            .fileHash(null) // file-service-client 响应中没有 fileHash
            .instantUpload(null) // file-service-client 响应中没有 instantUpload 标志
            .uploadTime(LocalDateTime.now())
            .accessLevel(clientResponse.getAccessLevel())
            .originalName(clientResponse.getOriginalName())
            .contentType(clientResponse.getContentType())
            .build();
    }

    /**
     * 删除临时文件
     * 
     * @param file 临时文件
     */
    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
                log.debug("临时文件删除成功: {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("临时文件删除失败: {}, 原因: {}", 
                    file.getAbsolutePath(), e.getMessage());
                // 不抛出异常，因为这不是关键错误
            }
        }
    }
}
