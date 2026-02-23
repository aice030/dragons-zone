package com.dragons.core.storage;

import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

/**
 * MinIO 存储实现
 *
 * @author aice
 * @since 2026-01-21
 */
@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucket;

    @Autowired
    public MinioStorageService(MinioClient minioClient, @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void upload(MultipartFile file, String objectName) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .contentType(file.getContentType())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO upload failed objectName={} bucket={}", objectName, bucket, e);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void upload(byte[] fileBytes, String contentType, String objectName) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .contentType(contentType)
                            .stream(new ByteArrayInputStream(fileBytes), fileBytes.length, -1)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO upload failed objectName={} bucket={}", objectName, bucket, e);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.warn("MinIO delete failed objectName={} bucket={}", objectName, bucket, e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            // 文件不存在或其他错误，返回false
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public String getPresignedUploadUrl(String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO generate upload presigned url failed objectName={} bucket={}", objectName, bucket, e);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }
}

