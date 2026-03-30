package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;
import lombok.Value;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 复合游标（R10）
 *
 * 编码格式：Base64Url(publishedAt|postId)
 * - publishedAt 使用 ISO_LOCAL_DATE_TIME
 * - postId 为 Long
 */
@Value
public class CursorToken {

    OffsetDateTime publishedAt;
    Long postId;

    public static String encode(CursorToken token) {
        if (token == null || token.publishedAt == null) {
            return null;
        }
        String payload = token.publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + token.postId;
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorToken decodeOrThrow(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length == 1) {
                // 兼容旧格式：仅包含 publishedAt（会跳过同一时间戳的记录，但不会重复）
                OffsetDateTime publishedAt = OffsetDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return new CursorToken(publishedAt, 0L);
            }
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor parts");
            }
            OffsetDateTime publishedAt = OffsetDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Long postId = Long.parseLong(parts[1]);
            return new CursorToken(publishedAt, postId);
        } catch (Exception e) {
            throw new ValidationException("cursor", "cursor 格式非法");
        }
    }
}

