package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体审核结果 DTO
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class MediaAuditResult {
    /**
     * 失败的媒体列表（包含 mediaId 和 title）
     */
    private List<FailedItem> failedItems;

    public MediaAuditResult() {
        this.failedItems = new ArrayList<>();
    }

    public MediaAuditResult(List<FailedItem> failedItems) {
        this.failedItems = failedItems != null ? failedItems : new ArrayList<>();
    }

    /**
     * 失败项（媒体ID和标题）
     */
    @Getter
    @Setter
    public static class FailedItem {
        private Long mediaId;
        private String title;

        public FailedItem() {
        }

        public FailedItem(Long mediaId, String title) {
            this.mediaId = mediaId;
            this.title = title;
        }
    }
}
