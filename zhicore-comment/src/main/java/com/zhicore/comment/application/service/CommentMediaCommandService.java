package com.zhicore.comment.application.service;

import com.zhicore.comment.application.port.CommentMediaUploadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评论媒体写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentMediaCommandService {

    private final CommentMediaUploadPort commentMediaUploadPort;

    public String uploadImage(MultipartFile file) {
        log.info("上传评论图片: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        String fileId = commentMediaUploadPort.uploadImage(file);
        log.info("评论图片上传成功: fileId={}", fileId);
        return fileId;
    }

    public String uploadVoice(MultipartFile file) {
        log.info("上传评论语音: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        String fileId = commentMediaUploadPort.uploadVoice(file);
        log.info("评论语音上传成功: fileId={}", fileId);
        return fileId;
    }
}
