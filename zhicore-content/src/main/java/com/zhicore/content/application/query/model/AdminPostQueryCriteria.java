package com.zhicore.content.application.query.model;

import com.zhicore.common.util.QueryParamValidator;
import com.zhicore.content.domain.model.PostStatus;
import lombok.Builder;
import lombok.Value;

/**
 * 管理端文章查询条件。
 *
 * 负责参数规范化与分页偏移量计算，
 * 避免控制层/应用服务重复揉查询参数细节。
 */
@Value
@Builder
public class AdminPostQueryCriteria {

    String keyword;
    String status;
    String statusCode;
    Long authorId;
    int page;
    int size;

    public static AdminPostQueryCriteria of(String keyword, String status, Long authorId, int page, int size) {
        String normalizedKeyword = QueryParamValidator.validateKeyword(keyword);
        String normalizedStatus = QueryParamValidator.validateStatus(status);
        String authorIdValue = authorId != null ? String.valueOf(authorId) : null;
        QueryParamValidator.validateId(authorIdValue);

        return AdminPostQueryCriteria.builder()
                .keyword(normalizedKeyword)
                .status(normalizedStatus)
                .statusCode(resolveStatusCode(normalizedStatus))
                .authorId(authorId)
                .page(QueryParamValidator.validatePage(page))
                .size(QueryParamValidator.validateSize(size))
                .build();
    }

    public int offset() {
        return QueryParamValidator.calculateOffset(page, size);
    }

    public boolean hasInvalidStatus() {
        return status != null && statusCode == null;
    }

    private static String resolveStatusCode(String status) {
        if (status == null) {
            return null;
        }

        try {
            return String.valueOf(PostStatus.valueOf(status).getCode());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
