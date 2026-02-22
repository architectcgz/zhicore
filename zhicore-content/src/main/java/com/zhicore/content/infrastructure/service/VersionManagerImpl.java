package com.zhicore.content.infrastructure.service;

import com.zhicore.content.domain.service.VersionManager;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import com.zhicore.content.infrastructure.mongodb.document.PostVersion;
import com.zhicore.content.infrastructure.mongodb.repository.PostContentRepository;
import com.zhicore.content.infrastructure.mongodb.repository.PostVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 版本管理器实现
 * 管理文章的版本历史，支持版本追踪、查询和恢复
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionManagerImpl implements VersionManager {

    private final PostVersionRepository versionRepository;
    private final PostContentRepository contentRepository;

    /**
     * 创建新版本
     * 在用户更新文章内容时，创建版本快照以追踪变更历史
     *
     * @param postId 文章ID
     * @param content 内容快照
     * @param editedBy 编辑者ID
     * @param changeNote 变更说明
     * @return 版本号
     */
    @Override
    @Transactional
    public Integer createVersion(String postId, String content, String editedBy, String changeNote) {
        log.info("Creating version for post: {}, editedBy: {}", postId, editedBy);

        // 获取当前最大版本号
        Integer nextVersion = getNextVersionNumber(postId);

        // 创建版本记录
        PostVersion version = PostVersion.builder()
                .postId(postId)
                .version(nextVersion)
                .content(content)
                .contentType("markdown") // 默认为 markdown，可以根据实际情况调整
                .editedBy(editedBy)
                .editedAt(LocalDateTime.now())
                .changeNote(changeNote)
                .build();

        versionRepository.save(version);

        log.info("Version created successfully: postId={}, version={}", postId, nextVersion);
        return nextVersion;
    }

    /**
     * 获取版本列表（分页）
     * 按编辑时间倒序返回版本列表
     *
     * @param postId 文章ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 版本列表
     */
    @Override
    public Page<PostVersion> getVersions(String postId, int page, int size) {
        log.debug("Getting versions for post: {}, page: {}, size: {}", postId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "editedAt"));
        return versionRepository.findByPostIdOrderByEditedAtDesc(postId, pageable);
    }

    /**
     * 获取特定版本
     * 查询指定版本号的完整内容快照
     *
     * @param postId 文章ID
     * @param version 版本号
     * @return 版本详情
     */
    @Override
    public Optional<PostVersion> getVersion(String postId, Integer version) {
        log.debug("Getting version: postId={}, version={}", postId, version);
        return versionRepository.findByPostIdAndVersion(postId, version);
    }

    /**
     * 恢复到指定版本
     * 将指定版本的内容设置为当前内容，并创建新版本记录
     *
     * @param postId 文章ID
     * @param version 版本号
     * @return 新的版本号
     */
    @Override
    @Transactional
    public Integer restoreVersion(String postId, Integer version) {
        log.info("Restoring post {} to version {}", postId, version);

        // 查询目标版本
        PostVersion targetVersion = versionRepository.findByPostIdAndVersion(postId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Version not found: postId=%s, version=%d", postId, version)));

        // 更新当前内容
        PostContent currentContent = contentRepository.findByPostId(postId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Post content not found: postId=%s", postId)));

        currentContent.setRaw(targetVersion.getContent());
        currentContent.setContentType(targetVersion.getContentType());
        currentContent.setUpdatedAt(LocalDateTime.now());
        contentRepository.save(currentContent);

        // 创建新版本记录（记录恢复操作）
        Integer newVersion = createVersion(
                postId,
                targetVersion.getContent(),
                targetVersion.getEditedBy(),
                String.format("Restored from version %d", version)
        );

        log.info("Post restored successfully: postId={}, fromVersion={}, newVersion={}",
                postId, version, newVersion);
        return newVersion;
    }

    /**
     * 清理旧版本
     * 当版本数量超过限制时，自动删除最旧的版本记录
     *
     * @param postId 文章ID
     * @param keepCount 保留的版本数
     */
    @Override
    @Transactional
    public void cleanOldVersions(String postId, int keepCount) {
        log.info("Cleaning old versions for post: {}, keepCount: {}", postId, keepCount);

        // 查询所有版本（按版本号倒序）
        List<PostVersion> allVersions = versionRepository.findByPostIdOrderByVersionDesc(postId);

        // 如果版本数量未超过限制，无需清理
        if (allVersions.size() <= keepCount) {
            log.debug("Version count ({}) is within limit ({}), no cleanup needed",
                    allVersions.size(), keepCount);
            return;
        }

        // 删除超出限制的旧版本
        List<PostVersion> versionsToDelete = allVersions.subList(keepCount, allVersions.size());
        versionRepository.deleteAll(versionsToDelete);

        log.info("Cleaned {} old versions for post: {}", versionsToDelete.size(), postId);
    }

    /**
     * 获取下一个版本号
     * 查询当前最大版本号并加1
     *
     * @param postId 文章ID
     * @return 下一个版本号
     */
    private Integer getNextVersionNumber(String postId) {
        Optional<PostVersion> latestVersion = versionRepository.findTopByPostIdOrderByVersionDesc(postId);
        return latestVersion.map(v -> v.getVersion() + 1).orElse(1);
    }
}
