package com.zhicore.upload.service;

import com.zhicore.upload.model.FileUploadResponse;
import com.platform.fileservice.client.model.AccessLevel;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传服务接口
 * 
 * 核心业务逻辑服务，主要负责文件验证和调用 file-service-client
 * 
 * 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
 */
public interface FileUploadService {
    
    /**
     * 上传图片文件
     * 
     * 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
     * 
     * @param file 文件对象
     * @param accessLevel 访问级别
     * @return 文件上传响应（包含 fileId 和 URL）
     */
    FileUploadResponse uploadImage(MultipartFile file, AccessLevel accessLevel);

    /**
     * 上传音频文件
     * 
     * 注意：秒传、分片上传等逻辑由 file-service-client 自动处理
     * 
     * @param file 文件对象
     * @param accessLevel 访问级别
     * @return 文件上传响应（包含 fileId 和 URL）
     */
    FileUploadResponse uploadAudio(MultipartFile file, AccessLevel accessLevel);
    
    /**
     * 批量上传图片
     * 
     * @param files 文件列表
     * @param accessLevel 访问级别
     * @return 文件上传响应列表
     */
    List<FileUploadResponse> uploadImagesBatch(List<MultipartFile> files, AccessLevel accessLevel);
    
    /**
     * 获取文件访问URL
     * 
     * @param fileId 文件ID
     * @return 文件访问URL
     */
    String getFileUrl(String fileId);
    
    /**
     * 删除文件
     * 
     * @param fileId 文件ID
     */
    void deleteFile(String fileId);
}
