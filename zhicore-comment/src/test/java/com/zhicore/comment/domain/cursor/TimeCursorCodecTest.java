package com.zhicore.comment.domain.cursor;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TimeCursorCodec 测试")
class TimeCursorCodecTest {

    private final TimeCursorCodec codec = new TimeCursorCodec();

    @Test
    @DisplayName("空游标应返回 null")
    void shouldReturnNullWhenCursorIsBlank() {
        assertNull(codec.decode(null));
        assertNull(codec.decode(""));
    }

    @Test
    @DisplayName("非法游标应返回参数错误码")
    void shouldThrowParamErrorWhenCursorIsInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> codec.decode("invalid-cursor"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("无效的分页游标", exception.getMessage());
    }

    @Test
    @DisplayName("旧版无 offset 的时间游标也应兼容")
    void shouldDecodeLegacyLocalTimestampCursor() {
        String raw = "2026-03-31T08:00:00_3001";
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        TimeCursorCodec.TimeCursor decoded = codec.decode(cursor);

        assertEquals(OffsetDateTime.parse("2026-03-31T08:00:00+08:00"), decoded.timestamp());
        assertEquals(3001L, decoded.commentId());
    }
}
