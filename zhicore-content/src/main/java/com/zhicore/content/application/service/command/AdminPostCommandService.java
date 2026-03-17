package com.zhicore.content.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端文章写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostCommandService {

    private final PostRepository postRepository;

    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.POST_NOT_FOUND));

        post.delete();
        postRepository.update(post);
        log.info("Admin deleted post: postId={}", postId);
    }
}
