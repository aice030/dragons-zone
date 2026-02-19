package com.dragons.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 媒体列表缓存值结构
 * <p>
 * 用于缓存媒体列表的ID和总数，避免缓存命中时还需要查询数据库获取total
 * </p>
 *
 * @author aice
 * @since 2026-02-19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaListCacheValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 列表总数
     */
    private Long total;

    /**
     * 媒体ID列表
     */
    private List<Long> mediaIds;
}
