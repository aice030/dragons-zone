package com.dragons.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 前端 OSS 上传用 STS 临时凭证
 * GET /api/media/upload/sts 返回
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OssStsCredentials {

    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private Long expiration;
    /** OSS 区域，如 oss-cn-beijing，用于前端 SDK 的 region */
    private String region;
    private String bucket;
}
