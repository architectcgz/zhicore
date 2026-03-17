package com.zhicore.content.application.service.command;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章标签写服务。
 *
 * 收口标签关联替换与剩余标签计算逻辑，
 * 避免上层写服务直接依赖标签关联仓储细节。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTagCommandService {

    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final TagCommandService tagCommandService;

    public ReplaceResult replaceTags(Long postId, List<String> tagNames) {
        List<Long> oldTagIds = postTagRepository.findTagIdsByPostId(postId);
        postTagRepository.detachAllByPostId(postId);

        List<Long> newTagIds = Collections.emptyList();
        if (tagNames != null && !tagNames.isEmpty()) {
            List<Tag> tags = tagCommandService.findOrCreateBatch(tagNames);
            newTagIds = tags.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
            postTagRepository.attachBatch(postId, newTagIds);
        }

        log.info("Post tags replaced via command service: postId={}, oldTagCount={}, newTagCount={}",
                postId, oldTagIds.size(), newTagIds.size());
        return new ReplaceResult(oldTagIds, newTagIds);
    }

    public List<String> listRemainingTagNames(Long postId, String removedSlug) {
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        return tagRepository.findByIdIn(tagIds).stream()
                .filter(tag -> !tag.getSlug().equals(removedSlug))
                .map(Tag::getName)
                .toList();
    }

    public record ReplaceResult(List<Long> oldTagIds, List<Long> newTagIds) {
    }
}
