package com.zhicore.content.application.service.command;

import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.commands.UpdatePostContentCommand;
import com.zhicore.content.application.command.commands.UpdatePostMetaCommand;
import com.zhicore.content.application.command.handlers.UpdatePostContentHandler;
import com.zhicore.content.application.command.handlers.UpdatePostMetaHandler;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.post.PostTagsUpdatedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUpdatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * 文章更新写服务。
 *
 * 收口文章元数据、正文、封面与标签联动更新，避免通用写服务继续膨胀。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostUpdateCommandService {

    private final OwnedPostLoadService ownedPostLoadService;
    private final PostRepository postRepository;
    private final PostTagCommandService postTagCommandService;
    private final PostCoverImageCommandService postCoverImageCommandService;
    private final UpdatePostMetaHandler updatePostMetaHandler;
    private final UpdatePostContentHandler updatePostContentHandler;
    private final IntegrationEventPublisher integrationEventPublisher;

    @Transactional
    public void updatePost(Long userId, Long postId, UpdatePostAppCommand request) {
        Post post = ownedPostLoadService.load(postId, userId);

        postCoverImageCommandService.handleCoverImageChange(postId, post.getCoverImageId(), request.coverImageId());

        if (request.topicId() != null) {
            post.setTopic(TopicId.of(request.topicId()));
            postRepository.update(post);
        }

        if (request.tags() != null) {
            PostTagCommandService.ReplaceResult replaceResult = postTagCommandService.replaceTags(postId, request.tags());
            integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
                    newEventId(),
                    Instant.now(),
                    postId,
                    replaceResult.oldTagIds(),
                    replaceResult.newTagIds(),
                    Instant.now(),
                    post.getVersion()
            ));
        }

        updatePostMetaHandler.handle(new UpdatePostMetaCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.title(),
                post.getExcerpt(),
                request.coverImageId()
        ));

        updatePostContentHandler.handle(new UpdatePostContentCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.content(),
                ContentType.MARKDOWN
        ));

        if (post.isPublished()) {
            integrationEventPublisher.publish(new PostUpdatedIntegrationEvent(
                    newEventId(),
                    Instant.now(),
                    post.getVersion(),
                    postId,
                    request.title(),
                    request.content(),
                    post.getExcerpt(),
                    Collections.emptyList()
            ));
        }

        log.info("Post updated: postId={}, userId={}", postId, userId);
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
