package com.dragons.core.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储抽象接口
 *
 * 目的：业务层不依赖具体存储（MinIO/OSS），未来迁移只需替换实现类。
 *
 * MVP阶段仅提供：上传、删除（回滚用）
 *
 * @author aice
 * @since 2026-01-21
 */
public interface StorageService {

    /**
     * 上传文件到对象存储
     *
     * @param file 文件
     * @param objectName 对象路径（例如 images/2026/01/21/xxx.jpg）
     */
    void upload(MultipartFile file, String objectName);

    /**
     * 上传文件字节数组到对象存储（用于幂等性场景，避免重复读取文件）
     *
     * @param fileBytes 文件字节数组
     * @param contentType 文件类型（例如 image/jpeg）
     * @param objectName 对象路径
     */
    void upload(byte[] fileBytes, String contentType, String objectName);

    /**
     * 删除对象（用于回滚或删除）
     *
     * @param objectName 对象路径
     */
    void delete(String objectName);

    /**
     * 检查对象是否存在
     *
     * @param objectName 对象路径
     * @return true=存在，false=不存在
     */
    boolean exists(String objectName);

    /**
     * 生成预签名URL（用于下载）
     *
     * @param objectName 对象路径
     * @param expirySeconds 过期时间（秒）
     * @return 预签名URL
     */
    String getPresignedUrl(String objectName, int expirySeconds);

    /**
     * 生成用于上传的预签名URL（通常为 HTTP PUT）
     *
     * @param objectName 对象路径
     * @param expirySeconds 过期时间（秒）
     * @return 预签名上传URL
     */
    String getPresignedUploadUrl(String objectName, int expirySeconds);
}

