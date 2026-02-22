package com.zhicore.upload.exception;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.platform.fileservice.client.exception.AccessDeniedException;
import com.platform.fileservice.client.exception.AuthenticationException;
import com.platform.fileservice.client.exception.InvalidRequestException;
import com.platform.fileservice.client.exception.NetworkException;
import com.platform.fileservice.client.exception.QuotaExceededException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 
 * 统一处理所有异常，返回标准的错误响应格式
 * 所有错误响应包含错误码、错误信息和 traceId
 * 
 * @author ZhiCore Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * 处理文件验证异常
     * 
     * 返回 400 Bad Request
     */
    @ExceptionHandler(FileValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleFileValidationException(FileValidationException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件验证失败: errorCode={}, message={}", 
            traceId, e.getErrorCode(), e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理文件服务异常
     * 
     * 根据错误码返回 500 或 503
     */
    @ExceptionHandler(FileServiceException.class)
    public ApiResponse<Void> handleFileServiceException(FileServiceException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        
        // 判断是服务不可用还是其他错误
        boolean isServiceUnavailable = ErrorCodes.FILE_SERVICE_UNAVAILABLE.equals(e.getErrorCode())
            || ErrorCodes.NETWORK_TIMEOUT.equals(e.getErrorCode());
        
        HttpStatus status = isServiceUnavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        
        log.error("[{}] 文件服务异常: errorCode={}, message={}, status={}", 
            traceId, e.getErrorCode(), e.getMessage(), status.value(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(status.value(), e.getMessage());
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理 Spring 文件大小超限异常
     * 
     * 返回 400 Bad Request
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件大小超过限制: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.BAD_REQUEST.value(), 
            "文件大小超过限制，最大允许 50MB"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理无效请求异常（文件服务客户端）
     * 
     * 返回 400 Bad Request
     */
    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidRequestException(InvalidRequestException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 无效的文件服务请求: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.BAD_REQUEST.value(), 
            "无效的请求参数"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理认证异常（文件服务客户端）
     * 
     * 返回 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件服务认证失败: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.UNAUTHORIZED.value(), 
            "认证失败，请重新登录"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理访问被拒绝异常（文件服务客户端）
     * 
     * 返回 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件服务拒绝访问: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.FORBIDDEN.value(), 
            "无权访问该文件"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理文件不存在异常（文件服务客户端）
     * 
     * 返回 404 Not Found
     */
    @ExceptionHandler(com.platform.fileservice.client.exception.FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleClientFileNotFoundException(
            com.platform.fileservice.client.exception.FileNotFoundException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件服务返回文件不存在: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.NOT_FOUND.value(), 
            "文件不存在"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理配额超限异常（文件服务客户端）
     * 
     * 返回 413 Payload Too Large
     */
    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleQuotaExceededException(QuotaExceededException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件大小超过限制: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.PAYLOAD_TOO_LARGE.value(), 
            "文件大小超过限制"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理网络异常（文件服务客户端）
     * 
     * 返回 503 Service Unavailable
     */
    @ExceptionHandler(NetworkException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleNetworkException(NetworkException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件服务网络错误: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.SERVICE_UNAVAILABLE.value(), 
            "文件服务暂时不可用，请稍后重试"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理文件服务通用异常（文件服务客户端）
     * 
     * 返回 500 Internal Server Error
     */
    @ExceptionHandler(com.platform.fileservice.client.exception.FileServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleClientFileServiceException(
            com.platform.fileservice.client.exception.FileServiceException e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 文件服务错误: statusCode={}, message={}", 
            traceId, e.getStatusCode(), e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            HttpStatus.INTERNAL_SERVER_ERROR.value(), 
            "文件服务错误"
        );
        response.setTraceId(traceId);
        return response;
    }

    /**
     * 处理通用异常
     * 
     * 返回 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        log.error("[{}] 系统内部错误: message={}", traceId, e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.fail(
            ResultCode.INTERNAL_ERROR.getCode(), 
            "系统内部错误，请稍后重试"
        );
        response.setTraceId(traceId);
        return response;
    }
}
