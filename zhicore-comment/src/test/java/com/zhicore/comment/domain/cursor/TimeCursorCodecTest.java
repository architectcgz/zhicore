package com.zhicore.comment.domain.cursor;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
