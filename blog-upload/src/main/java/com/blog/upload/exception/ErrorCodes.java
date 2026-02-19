package com.blog.upload.exception;

/**
 * 错误码常量
 * 
 * 定义所有文件上传服务的错误码
 */
public final class ErrorCodes {

    private ErrorCodes() {
        // 工具类，禁止实例化
    }

    /**
     * 文件不能为空
     */
    public static final String FILE_EMPTY = "UPLOAD_001";

    /**
     * 不支持的文件类型
     */
    public static final String INVALID_FILE_TYPE = "UPLOAD_002";

    /**
     * 文件大小超过限制
     */
    public static final String FILE_SIZE_EXCEEDED = "UPLOAD_003";

    /**
     * 文件哈希计算失败
     */
    public static final String HASH_CALCULATION_FAILED = "UPLOAD_004";

    /**
     * 文件上传失败
     */
    public static final String UPLOAD_FAILED = "UPLOAD_005";

    /**
     * 文件服务不可用
     */
    public static final String FILE_SERVICE_UNAVAILABLE = "UPLOAD_006";

    /**
     * 文件删除失败
     */
    public static final String DELETE_FAILED = "UPLOAD_007";

    /**
     * 文件不存在
     */
    public static final String FILE_NOT_FOUND = "UPLOAD_008";

    /**
     * 上传任务不存在
     */
    public static final String UPLOAD_TASK_NOT_FOUND = "UPLOAD_009";

    /**
     * 分片上传初始化失败
     */
    public static final String MULTIPART_INIT_FAILED = "UPLOAD_010";

    /**
     * 分片上传失败
     */
    public static final String MULTIPART_UPLOAD_FAILED = "UPLOAD_011";

    /**
     * 网络超时
     */
    public static final String NETWORK_TIMEOUT = "UPLOAD_012";

    /**
     * 系统内部错误
     */
    public static final String INTERNAL_ERROR = "UPLOAD_999";
}
