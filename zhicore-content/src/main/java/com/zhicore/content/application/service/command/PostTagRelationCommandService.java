package com.zhicore.content.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.domain.model.Post;
import com.zhicore.integration.messaging.post.PostTagsUpdatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 文章标签关系写服务。
 *
 * 收口标签替换与移除用例，保持标签维护策略独立于文章内容更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTagRelationCommandService {

    private final OwnedPostLoadService ownedPostLoadService;
    private final PostTagCommandService postTagCommandService;
    private final IntegrationEventPublisher integrationEventPublisher;

    @Transactional
    public void replacePostTags(Long userId, Long postId, List<String> tagNames) {
        Post post = ownedPostLoadService.load(postId, userId);

        if (tagNames != null && tagNames.size() > 10) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
        }

        PostTagCommandService.ReplaceResult replaceResult = postTagCommandService.replaceTags(postId, tagNames);
        integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
                newEventId(),
                Instant.now(),
                postId,
                replaceResult.oldTagIds(),
                replaceResult.newTagIds(),
                Instant.now(),
                post.getVersion()
        ));

        log.info("Post tags replaced: postId={}, userId={}, newTagCount={}",
                postId, userId, tagNames != null ? tagNames.size() : 0);
    }

    @Transactional
    public void detachTag(Long userId, Long postId, String slug) {
        ownedPostLoadService.load(postId, userId);
        List<String> remainingTagNames = postTagCommandService.listRemainingTagNames(postId, slug);
        replacePostTags(userId, postId, remainingTagNames);
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
