package com.zhicore.content.application.service.command;

import com.zhicore.content.application.command.commands.DeletePostCommand;
import com.zhicore.content.application.command.handlers.DeletePostHandler;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.application.service.PostContentImageCleanupService;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章生命周期写服务。
 *
 * 收口撤回发布与删除文章用例，避免 PostWriteService 同时承担文章维护和生命周期流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLifecycleCommandService {

    private final OwnedPostLoadService ownedPostLoadService;
    private final PostRepository postRepository;
    private final PostCoverImageCommandService postCoverImageCommandService;
    private final DeletePostHandler deletePostHandler;
    private final PostContentImageCleanupService postContentImageCleanupService;

    @Transactional
    public void unpublishPost(Long userId, Long postId) {
        Post post = ownedPostLoadService.load(postId, userId);
        post.unpublish();
        postRepository.update(post);
        log.info("Post unpublished: postId={}, userId={}", postId, userId);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = ownedPostLoadService.load(postId, userId);

        postCoverImageCommandService.deleteCoverImage(postId, post.getCoverImageId());
        deletePostHandler.handle(new DeletePostCommand(
                PostId.of(postId),
                UserId.of(userId)
        ));

        try {
            postContentImageCleanupService.cleanupContentImagesAsync(postId);
        } catch (Exception e) {
            log.warn("Failed to schedule content image cleanup: postId={}", postId, e);
        }

        log.info("Post deleted: postId={}, userId={}", postId, userId);
    }
}
