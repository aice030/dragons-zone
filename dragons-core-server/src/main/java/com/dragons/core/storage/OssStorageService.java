package com.dragons.core.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Date;

/**
 * 阿里云 OSS 存储实现
 *
 * @author aice
 * @since 2026-02-15
 */
@Slf4j
@Service
@Primary
public class OssStorageService implements StorageService {

    private final OSS ossClient;
    private final String bucket;

    @Autowired
    public OssStorageService(OSS ossClient, @Value("${oss.bucket}") String bucket) {
        this.ossClient = ossClient;
        this.bucket = bucket;
    }

    @Override
    public void upload(MultipartFile file, String objectName) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }
            PutObjectRequest request = new PutObjectRequest(bucket, objectName, file.getInputStream(), metadata);
            ossClient.putObject(request);
        } catch (Exception e) {
            log.error("OSS upload failed objectName={} bucket={}", objectName, bucket, e);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void upload(byte[] fileBytes, String contentType, String objectName) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(fileBytes.length);
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            PutObjectRequest request = new PutObjectRequest(bucket, objectName, inputStream, metadata);
            ossClient.putObject(request);
        } catch (Exception e) {
            log.error("OSS upload failed objectName={} bucket={}", objectName, bucket, e);
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            ossClient.deleteObject(bucket, objectName);
        } catch (Exception e) {
            log.warn("OSS delete failed objectName={} bucket={}", objectName, bucket, e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            return ossClient.doesObjectExist(bucket, objectName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectName);
            request.setExpiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L));
            URL url = ossClient.generatePresignedUrl(request);
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.FILE_UPLOAD_FAILED);
        }
    }
}
