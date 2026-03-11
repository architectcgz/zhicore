package com.zhicore.content.application.service;

import com.zhicore.content.application.port.client.FileResourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 文章封面图写服务。
 *
 * 收口封面图校验与替换/删除时的文件清理逻辑，
 * 避免 PostWriteService 直接依赖文件服务细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCoverImageCommandService {

    private static final Pattern UUID_V7 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private final FileResourceClient fileResourceClient;

    public void validateFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }

        if (!UUID_V7.matcher(fileId).matches()) {
            throw new IllegalArgumentException("无效的 fileId 格式: " + fileId);
        }
    }

    public void handleCoverImageChange(Long postId, String oldCoverImageId, String newCoverImageId) {
        if (newCoverImageId == null || newCoverImageId.isBlank()) {
            return;
        }

        validateFileId(newCoverImageId);
        if (oldCoverImageId == null || oldCoverImageId.isBlank() || oldCoverImageId.equals(newCoverImageId)) {
            return;
        }

        deleteCoverImage(postId, oldCoverImageId, "删除旧封面图");
    }

    public void deleteCoverImage(Long postId, String coverImageId) {
        deleteCoverImage(postId, coverImageId, "删除封面图");
    }

    private void deleteCoverImage(Long postId, String coverImageId, String action) {
        if (coverImageId == null || coverImageId.isBlank()) {
            return;
        }

        FileResourceClient.DeleteResult result = fileResourceClient.deleteFile(coverImageId);
        if (result.success()) {
            log.info("{}: postId={}, fileId={}", action, postId, coverImageId);
            return;
        }

        log.error("{}失败: postId={}, fileId={}, error={}",
                action, postId, coverImageId, result.message());
    }
}
