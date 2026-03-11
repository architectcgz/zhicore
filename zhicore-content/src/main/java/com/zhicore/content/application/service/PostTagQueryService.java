package com.zhicore.content.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.zhicore.content.application.port.repo.PostRepository;

/**
 * 文章标签查询服务。
 *
 * 聚合文章存在性校验、标签关联查询与标签 DTO 组装，
 * 避免这些读侧细节散落在更高层的应用服务中。
 */
@Service
@RequiredArgsConstructor
public class PostTagQueryService {

    private final PostRepository postRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;

    public List<TagDTO> getPostTags(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        if (post.isDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }

        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        return tagRepository.findByIdIn(tagIds).stream()
                .map(tag -> TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .description(tag.getDescription())
                        .createdAt(tag.getCreatedAt())
                        .updatedAt(tag.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
