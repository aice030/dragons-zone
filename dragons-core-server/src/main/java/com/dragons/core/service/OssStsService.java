package com.dragons.core.service;

import com.dragons.core.dto.OssStsCredentials;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云 STS 临时凭证服务
 * 供前端 OSS SDK（如分片上传）使用
 */
@Slf4j
@Service
public class OssStsService {

    private static final String STS_API_VERSION = "2015-04-01";

    @Value("${oss.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${oss.bucket:}")
    private String bucket;

    /** OSS endpoint 如 oss-cn-beijing.aliyuncs.com，用于推导 region 如 oss-cn-beijing */
    @Value("${oss.endpoint:}")
    private String endpoint;

    @Value("${oss.sts.region:cn-hangzhou}")
    private String stsRegion;

    @Value("${oss.sts.role-arn:}")
    private String roleArn;

    @Value("${oss.sts.role-session-name:dragons-frontend-upload}")
    private String roleSessionName;

    @Value("${oss.sts.duration-seconds:3600}")
    private long durationSeconds;

    /**
     * 获取 STS 临时凭证，供前端直传 OSS 使用（如准备上传响应中附带）
     *
     * @return 临时凭证 + region、bucket；若未配置 STS 则返回 null
     */
    public OssStsCredentials getStsCredentials() {
        if (roleArn == null || roleArn.isBlank() || roleArn.contains("你的")) {
            log.warn("OSS STS not configured: oss.sts.role-arn is missing or placeholder, returning null");
            return null;
        }
        log.info("Requesting STS credentials roleArn={} stsRegion={} durationSeconds={}", roleArn, stsRegion, durationSeconds);
        try {
            DefaultProfile profile = DefaultProfile.getProfile(stsRegion, accessKeyId, accessKeySecret);
            DefaultAcsClient client = new DefaultAcsClient(profile);

            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setVersion(STS_API_VERSION);
            request.setRoleArn(roleArn);
            request.setRoleSessionName(roleSessionName);
            request.setDurationSeconds(durationSeconds);

            AssumeRoleResponse response = client.getAcsResponse(request);
            AssumeRoleResponse.Credentials credentials = response.getCredentials();
            Long expirationSeconds = null;
            Object exp = credentials.getExpiration();
            if (exp instanceof java.util.Date) {
                expirationSeconds = ((java.util.Date) exp).getTime() / 1000;
            }
            String region = endpoint != null && !endpoint.isEmpty() ? endpoint : "oss-cn-beijing";
            // 前端 ali-oss SDK 要求 region 为地域 id（如 oss-cn-beijing），不能带 .aliyuncs.com
            if (region.contains(".aliyuncs.com")) {
                region = region.substring(0, region.indexOf(".aliyuncs.com"));
            }
            log.info("STS credentials obtained successfully region={} bucket={} expirationSeconds={}", region, bucket, expirationSeconds);
            return new OssStsCredentials(
                    credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(),
                    credentials.getSecurityToken(),
                    expirationSeconds,
                    region,
                    bucket
            );
        } catch (ClientException e) {
            log.error("STS AssumeRole failed roleArn={} stsRegion={}, returning null", roleArn, stsRegion, e);
            return null;
        }
    }
}
