package com.zhicore.content.application.service.command;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
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
        PostId postIdRef = PostId.of(postId);
        List<TagId> oldTagIds = postTagRepository.findTagIdsByPostId(postIdRef);
        postTagRepository.detachAllByPostId(postIdRef);

        List<TagId> newTagIds = Collections.emptyList();
        if (tagNames != null && !tagNames.isEmpty()) {
            List<Tag> tags = tagCommandService.findOrCreateBatch(tagNames);
            newTagIds = tags.stream()
                    .map(Tag::getId)
                    .map(TagId::of)
                    .collect(Collectors.toList());
            postTagRepository.attachBatch(postIdRef, newTagIds);
        }

        log.info("Post tags replaced via command service: postId={}, oldTagCount={}, newTagCount={}",
                postId, oldTagIds.size(), newTagIds.size());
        return new ReplaceResult(
                oldTagIds.stream().map(TagId::getValue).toList(),
                newTagIds.stream().map(TagId::getValue).toList());
    }

    public List<String> listRemainingTagNames(Long postId, String removedSlug) {
        List<TagId> tagIds = postTagRepository.findTagIdsByPostId(PostId.of(postId));
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        return tagRepository.findByIdIn(tagIds.stream().map(TagId::getValue).toList()).stream()
                .filter(tag -> !tag.getSlug().equals(removedSlug))
                .map(Tag::getName)
                .toList();
    }

    public record ReplaceResult(List<Long> oldTagIds, List<Long> newTagIds) {
    }
}
