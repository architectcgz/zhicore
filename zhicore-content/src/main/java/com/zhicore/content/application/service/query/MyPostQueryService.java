package com.zhicore.content.application.service.query;

import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.service.PostFileUrlResolver;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 我的文章列表查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyPostQueryService {

    private final PostRepository postRepository;
    private final PostFileUrlResolver postFileUrlResolver;

    public List<PostBriefVO> getMyPosts(Long userId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findByOwnerId(userId, PostStatus.valueOf(status), offset, size);
        log.debug("Query my posts: userId={}, status={}, page={}, size={}, count={}",
                userId, status, page, size, posts.size());
        return posts.stream()
                .map(this::toBriefVO)
                .collect(Collectors.toList());
    }

    private PostBriefVO toBriefVO(Post post) {
        PostBriefVO vo = PostViewAssembler.toBriefVO(post);
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            vo.setCoverImageUrl(postFileUrlResolver.resolve(post.getCoverImageId()));
        }
        return vo;
    }
}
