package com.zhicore.upload.exception;

import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试
 * 
 * 验证异常处理器正确处理各种异常并返回标准响应格式
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private static final String TEST_TRACE_ID = "test-trace-123";

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.put("traceId", TEST_TRACE_ID);
    }

    @Test
    void testHandleFileValidationException() {
        // Given
        FileValidationException exception = new FileValidationException(
            ErrorCodes.INVALID_FILE_TYPE,
            "不支持的文件类型"
        );

        // When
        ApiResponse<Void> response = handler.handleFileValidationException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
        assertEquals("不支持的文件类型", response.getMessage());
        assertEquals(TEST_TRACE_ID, response.getTraceId());
    }

    @Test
    void testHandleFileServiceException_ServiceUnavailable() {
        // Given
        FileServiceException exception = new FileServiceException(
            ErrorCodes.FILE_SERVICE_UNAVAILABLE,
            "文件服务不可用"
        );

        // When
        ApiResponse<Void> response = handler.handleFileServiceException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getCode());
        assertEquals("文件服务不可用", response.getMessage());
        assertEquals(TEST_TRACE_ID, response.getTraceId());
    }

    @Test
    void testHandleFileServiceException_InternalError() {
        // Given
        FileServiceException exception = new FileServiceException(
            ErrorCodes.UPLOAD_FAILED,
            "文件上传失败"
        );

        // When
        ApiResponse<Void> response = handler.handleFileServiceException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getCode());
        assertEquals("文件上传失败", response.getMessage());
        assertEquals(TEST_TRACE_ID, response.getTraceId());
    }

    @Test
    void testHandleMaxUploadSizeExceededException() {
        // Given
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(50 * 1024 * 1024);

        // When
        ApiResponse<Void> response = handler.handleMaxUploadSizeExceededException(exception);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getCode());
        assertTrue(response.getMessage().contains("文件大小超过限制"));
        assertEquals(TEST_TRACE_ID, response.getTraceId());
    }

    @Test
    void testHandleException() {
        // Given
        Exception exception = new RuntimeException("未知错误");

        // When
        ApiResponse<Void> response = handler.handleException(exception);

        // Then
        assertNotNull(response);
        assertEquals(1000, response.getCode()); // ResultCode.INTERNAL_ERROR.getCode()
        assertTrue(response.getMessage().contains("系统内部错误"));
        assertEquals(TEST_TRACE_ID, response.getTraceId());
    }
}
