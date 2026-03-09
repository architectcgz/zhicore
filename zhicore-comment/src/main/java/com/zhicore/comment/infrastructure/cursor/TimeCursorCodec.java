package com.zhicore.comment.infrastructure.cursor;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 时间排序游标编解码器
 * 游标格式：Base64(ISO-8601 时间戳 + "_" + commentId)
 * 加入 commentId 保证游标唯一性（同一时间可能有多条评论）
 *
 * @author ZhiCore Team
 */
@Component
public class TimeCursorCodec {

    private static final String SEPARATOR = "_";

    /**
     * 编码游标
     */
    public String encode(LocalDateTime timestamp, Long commentId) {
        String raw = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + SEPARATOR + commentId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从评论编码游标
     */
    public String encode(Comment comment) {
        return encode(comment.getCreatedAt(), comment.getId());
    }

    /**
     * 解码游标
     */
    public TimeCursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int lastSeparator = raw.lastIndexOf(SEPARATOR);

            String timestampStr = raw.substring(0, lastSeparator);
            String commentIdStr = raw.substring(lastSeparator + 1);
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Long commentId = Long.parseLong(commentIdStr);

            return new TimeCursor(timestamp, commentId);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的分页游标");
        }
    }

    /**
     * 时间游标记录
     */
    public record TimeCursor(LocalDateTime timestamp, Long commentId) {}
}
