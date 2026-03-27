package com.zhicore.content.application.service.query;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签文章分页查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagPostQueryService {

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final PostRepository postRepository;

    public PageResult<PostVO> getPostsByTag(String slug, int page, int size) {
        log.debug("Getting posts by tag: slug={}, page={}, size={}", slug, page, size);

        Tag tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + slug));

        Pageable pageable = PageRequest.of(page, size);
        Page<PostId> postIdPage = postTagRepository.findPostIdsByTagId(TagId.of(tag.getId()), pageable);
        if (postIdPage.isEmpty()) {
            return PageResult.of(page, size, postIdPage.getTotalElements(), Collections.emptyList());
        }

        List<Long> postIds = postIdPage.getContent().stream().map(PostId::getValue).toList();
        Map<Long, Post> postMap = postRepository.findByIds(postIds);
        List<PostVO> postVOs = postIds.stream()
                .map(postMap::get)
                .filter(post -> post != null)
                .map(PostViewAssembler::toVO)
                .collect(Collectors.toList());

        return PageResult.of(page, size, postIdPage.getTotalElements(), postVOs);
    }
}
