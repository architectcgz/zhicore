package com.zhicore.content.application.service.query;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 文章批量查询服务。
 */
@Service
@RequiredArgsConstructor
public class PostBatchQueryService {

    private final PostRepository postRepository;

    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Post> posts = postRepository.findByIds(new ArrayList<>(postIds));
        Map<Long, PostDTO> result = new HashMap<>();
        for (Map.Entry<Long, Post> entry : posts.entrySet()) {
            Post post = entry.getValue();
            if (post == null || post.isDeleted()) {
                continue;
            }
            PostDTO dto = new PostDTO();
            dto.setId(post.getId().getValue());
            dto.setTitle(post.getTitle());
            dto.setOwnerId(post.getOwnerId().getValue());
            dto.setStatus(post.getStatus().name());
            dto.setCreatedAt(post.getCreatedAt());
            dto.setPublishedAt(post.getPublishedAt());
            result.put(post.getId().getValue(), dto);
        }
        return result;
    }
}
