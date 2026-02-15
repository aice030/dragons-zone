package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 媒体审核请求 DTO
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class MediaAuditRequest {
    /**
     * 要审核的媒体ID列表
     */
    private List<Long> mediaIds;
}
