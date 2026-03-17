package com.zhicore.content.application.service.query;

import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.assembler.AdminPostManageAssembler;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.query.model.AdminPostQueryCriteria;
import com.zhicore.content.domain.model.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端文章列表查询服务。
 *
 * 负责执行管理端文章列表查询与 DTO 映射，
 * 避免 facade 继续混入查询条件细节与装配逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostListQueryService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public PageResult<PostManageDTO> query(AdminPostQueryCriteria criteria) {
        if (criteria.hasInvalidStatus()) {
            log.warn("Invalid status value: {}", criteria.getStatus());
        }

        List<Post> posts = postRepository.findByConditions(
                criteria.getKeyword(),
                criteria.getStatusCode(),
                criteria.getAuthorId(),
                criteria.offset(),
                criteria.getSize()
        );
        long total = postRepository.countByConditions(
                criteria.getKeyword(),
                criteria.getStatusCode(),
                criteria.getAuthorId()
        );

        List<PostManageDTO> records = posts.stream()
                .map(AdminPostManageAssembler::toDTO)
                .toList();

        log.debug("Query admin posts: keyword={}, status={}, statusCode={}, authorId={}, page={}, size={}, total={}",
                criteria.getKeyword(),
                criteria.getStatus(),
                criteria.getStatusCode(),
                criteria.getAuthorId(),
                criteria.getPage(),
                criteria.getSize(),
                total);
        return PageResult.of(criteria.getPage(), criteria.getSize(), total, records);
    }
}
