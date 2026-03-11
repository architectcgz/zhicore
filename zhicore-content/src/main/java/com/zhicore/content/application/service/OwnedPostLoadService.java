package com.zhicore.content.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 作者文章加载服务。
 *
 * 统一收口“按作者身份加载文章并校验所有权”逻辑，
 * 避免 read/write command service 各自复制权限校验。
 */
@Service
@RequiredArgsConstructor
public class OwnedPostLoadService {

    private final PostRepository postRepository;

    public Post load(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        if (!post.isOwnedBy(UserId.of(userId))) {
            throw new ForbiddenException("无权操作此文章");
        }
        return post;
    }
}
