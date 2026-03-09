package com.zhicore.upload.infrastructure.sentinel;

/**
 * 文件上传服务 Sentinel 方法级资源常量。
 */
public final class UploadSentinelResources {

    private UploadSentinelResources() {
    }

    public static final String UPLOAD_IMAGE = "upload:uploadImage";
    public static final String UPLOAD_AUDIO = "upload:uploadAudio";
    public static final String UPLOAD_IMAGES_BATCH = "upload:uploadImagesBatch";
    public static final String GET_FILE_URL = "upload:getFileUrl";
    public static final String DELETE_FILE = "upload:deleteFile";
}
