package com.zhicore.common.exception;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ErrorInfo;
import com.zhicore.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理领域异常
     */
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDomainException(DomainException e, HttpServletRequest request) {
        log.warn("领域异常: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorizedException(UnauthorizedException e) {
        return ApiResponse.fail(ResultCode.UNAUTHORIZED, e.getMessage());
    }

    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbiddenException(ForbiddenException e) {
        return ApiResponse.fail(ResultCode.FORBIDDEN, e.getMessage());
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleResourceNotFoundException(ResourceNotFoundException e) {
        return ApiResponse.fail(ResultCode.NOT_FOUND, e.getMessage());
    }

    /**
     * 处理乐观锁并发冲突异常（HTTP 409）
     */
    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<ErrorInfo> handleOptimisticLockException(OptimisticLockException e, HttpServletRequest request) {
        log.warn("并发冲突: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(
                ResultCode.CONFLICT.getCode(),
                e.getMessage(),
                new ErrorInfo(e.getErrorCode(), e.isRetrySuggested())
        );
    }

    /**
     * 处理请求过于频繁异常（HTTP 429）
     */
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleTooManyRequestsException(TooManyRequestsException e, HttpServletRequest request) {
        log.warn("请求过于频繁: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(ResultCode.TOO_MANY_REQUESTS, e.getMessage());
    }

    /**
     * 处理依赖服务不可用异常（HTTP 503）
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleServiceUnavailableException(ServiceUnavailableException e, HttpServletRequest request) {
        log.warn("依赖服务不可用: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /**
     * 处理自定义参数校验异常
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleValidationException(ValidationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        if (e.getFieldErrors() != null && !e.getFieldErrors().isEmpty()) {
            return ApiResponse.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage(), e.getFieldErrors());
        }
        return ApiResponse.fail(ResultCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 处理参数校验异常 - @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getDefaultMessage()
                        : ((ObjectError) error).getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理控制器方法参数校验异常（Spring 6 HandlerMethodValidationException）
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "参数校验失败")
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理参数校验异常 - @Validated
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ApiResponse.fail(ResultCode.PARAM_ERROR, "缺少参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} - {}", e.getName(), e.getValue());
        return ApiResponse.fail(ResultCode.PARAM_ERROR, "参数类型错误: " + e.getName());
    }

    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ApiResponse.fail(ResultCode.PARAM_ERROR, "请求体格式错误");
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        return ApiResponse.fail(ResultCode.METHOD_NOT_ALLOWED, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        String requestUrl = e.getRequestURL();
        
        // 过滤浏览器自动请求的静态资源，避免日志噪音
        if (isBrowserAutoRequest(requestUrl)) {
            log.debug("静态资源未找到: {} - {}", requestUrl, e.getMessage());
        } else {
            log.warn("接口不存在: {}", requestUrl);
        }
        
        return ApiResponse.fail(ResultCode.NOT_FOUND, "接口不存在: " + requestUrl);
    }
    
    /**
     * 判断是否为浏览器自动请求的资源
     */
    private boolean isBrowserAutoRequest(String url) {
        if (url == null) {
            return false;
        }
        // 常见的浏览器自动请求路径
        return url.contains("favicon.ico") 
            || url.contains(".well-known")
            || url.contains("apple-touch-icon")
            || url.contains("browserconfig.xml")
            || url.contains("manifest.json");
    }

    /**
     * 处理非法参数异常（包括枚举转换失败、值对象验证错误和内容类型验证错误）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        String message = e.getMessage();
        
        // 检查是否是内容类型验证错误
        if (message != null && message.contains("内容类型")) {
            log.warn("内容类型验证失败: {} - {}", request.getRequestURI(), message);
            return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
        }
        
        // 检查是否是枚举转换失败
        if (message != null && message.contains("No enum constant")) {
            log.warn("参数值无效: {} - {}", request.getRequestURI(), message);
            // 提取枚举类型名称
            String enumType = message.substring(message.lastIndexOf('.') + 1);
            return ApiResponse.fail(ResultCode.PARAM_ERROR, "参数值无效: " + enumType);
        }
        
        // 检查是否是值对象验证错误（PostId、UserId、TagId、TopicId 等）
        if (message != null && (message.contains("Id 值必须为正数") || 
                                message.contains("ID 值必须为正数") ||
                                message.contains("must be positive"))) {
            log.warn("值对象验证失败: {} - {}", request.getRequestURI(), message);
            return ApiResponse.fail(ResultCode.PARAM_ERROR, message);
        }
        
        log.warn("非法参数: {} - {}", request.getRequestURI(), message);
        return ApiResponse.fail(ResultCode.PARAM_ERROR, "参数值无效");
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String errorMessage = e.getMessage();
        String exceptionClassName = e.getClass().getName();
        
        // 过滤静态资源404错误，避免日志噪音
        if (isStaticResourceNotFound(requestUri, errorMessage)) {
            log.debug("静态资源未找到: {} - {}", requestUri, errorMessage);
            return ApiResponse.fail(ResultCode.NOT_FOUND, "资源不存在");
        }
        
        // 过滤 OpenAPI 相关异常，避免日志噪音
        if (isOpenApiException(requestUri, errorMessage, exceptionClassName)) {
            log.debug("OpenAPI 资源请求: {} - {}", requestUri, errorMessage);
            return ApiResponse.fail(ResultCode.NOT_FOUND, "API 文档资源不存在");
        }
        
        log.error("系统异常: {} - {}", requestUri, errorMessage, e);
        return ApiResponse.fail(ResultCode.FAIL, "系统繁忙，请稍后重试");
    }
    
    /**
     * 判断是否为 OpenAPI 相关异常
     */
    private boolean isOpenApiException(String uri, String errorMessage, String exceptionClassName) {
        if (uri == null || errorMessage == null) {
            return false;
        }
        
        // 检查是否为 OpenAPI 相关路径
        boolean isOpenApiPath = uri.contains("/v3/api-docs") 
            || uri.contains("/swagger-config")
            || uri.contains("/swagger-resources");
        
        // 检查异常类型和消息
        boolean isOpenApiException = exceptionClassName.contains("OpenApiResourceNotFoundException")
            || errorMessage.contains("No OpenAPI resource found")
            || errorMessage.contains("swagger-config");
        
        return isOpenApiPath && isOpenApiException;
    }
    
    /**
     * 判断是否为静态资源未找到错误
     */
    private boolean isStaticResourceNotFound(String uri, String errorMessage) {
        if (uri == null || errorMessage == null) {
            return false;
        }
        
        // 检查是否为浏览器自动请求的静态资源
        boolean isBrowserAutoRequest = uri.contains("favicon.ico") 
            || uri.contains(".well-known")
            || uri.contains("apple-touch-icon")
            || uri.contains("browserconfig.xml")
            || uri.contains("manifest.json");
        
        // 检查错误消息是否包含 "No static resource"
        boolean isStaticResourceError = errorMessage.contains("No static resource");
        
        return isBrowserAutoRequest && isStaticResourceError;
    }
}
