package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 媒体上传结果通知请求 DTO
 *
 * 对应接口：POST /api/media/upload/complete
 */
@Getter
@Setter
public class MediaUploadCompleteRequest {

    /**
     * 准备上传阶段返回的 media 主键
     */
    private Long mediaId;

    /**
     * true=上传成功，false=上传失败
     */
    private Boolean success;

    /**
     * JSON 数组字符串，例如 "[1,2,3]" 或 "[]"
     */
    private String visibleUserIds;

    /**
     * 封面数据（如 base64 或前端上传后得到的路径，由实现约定）
     * 当前实现阶段封面在准备上传阶段已处理，此字段可用于后续扩展或日志。
     */
    private String cover;

    /**
     * 失败时的错误码（可选）
     */
    private String code;

    /**
     * 失败时的错误描述（可选）
     */
    private String message;
}

