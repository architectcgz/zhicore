package com.zhicore.comment.infrastructure.cursor;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 热度排序游标编解码器
 * 游标格式：Base64("{likeCount}_{commentId}")
 * 使用 Base64 编码避免特殊字符问题，同时隐藏内部实现细节
 *
 * @author ZhiCore Team
 */
@Component
public class HotCursorCodec {

    private static final String SEPARATOR = "_";

    /**
     * 编码游标
     */
    public String encode(int likeCount, Long commentId) {
        String raw = likeCount + SEPARATOR + commentId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从评论编码游标
     */
    public String encode(Comment comment) {
        return encode(comment.getStats().getLikeCount(), comment.getId());
    }

    /**
     * 解码游标
     */
    public HotCursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(SEPARATOR);

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }

            return new HotCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BusinessException("无效的分页游标");
        }
    }

    /**
     * 热度游标记录
     */
    public record HotCursor(int likeCount, Long commentId) {}
}
