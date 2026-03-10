package com.zhicore.upload.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.platform.fileservice.client.model.AccessLevel;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("UploadSentinelHandlers 测试")
class UploadSentinelHandlersTest {

    @Test
    @DisplayName("获取文件地址 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForGetFileUrl() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> UploadSentinelHandlers.handleGetFileUrlBlocked("file-1", new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("批量上传 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForBatchUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> UploadSentinelHandlers.handleUploadImagesBatchBlocked(List.of(file), AccessLevel.PUBLIC,
                        new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
