package com.zhicore.comment.application.port;

import org.springframework.web.multipart.MultipartFile;

/**
 * 评论媒体上传端口。
 */
public interface CommentMediaUploadPort {

    String uploadImage(MultipartFile file);

    String uploadVoice(MultipartFile file);
}
