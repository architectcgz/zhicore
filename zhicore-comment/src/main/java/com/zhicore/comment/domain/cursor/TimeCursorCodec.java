package com.zhicore.comment.domain.cursor;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final DateTimeFormatter OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter LEGACY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 编码游标
     */
    public String encode(OffsetDateTime timestamp, Long commentId) {
        String raw = timestamp.format(OFFSET_FORMATTER) + SEPARATOR + commentId;
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
            OffsetDateTime timestamp = decodeTimestamp(timestampStr);
            Long commentId = Long.parseLong(commentIdStr);

            return new TimeCursor(timestamp, commentId);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的分页游标");
        }
    }

    private OffsetDateTime decodeTimestamp(String timestampStr) {
        try {
            return OffsetDateTime.parse(timestampStr, OFFSET_FORMATTER);
        } catch (Exception ignore) {
            LocalDateTime legacy = LocalDateTime.parse(timestampStr, LEGACY_FORMATTER);
            return legacy.atOffset(ZoneOffset.ofHours(8));
        }
    }

    /**
     * 时间游标记录
     */
    public record TimeCursor(OffsetDateTime timestamp, Long commentId) {}
}
