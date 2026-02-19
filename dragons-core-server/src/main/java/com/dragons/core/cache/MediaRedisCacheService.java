package com.dragons.core.cache;

import com.dragons.core.entity.Media;

/**
 * 媒体核心数据 Redis 缓存服务
 * <p>
 * 按 Redis_DESIGN.md 设计：
 * - Key 格式：media:core:{mediaId}
 * - TTL：600 秒（10 分钟）
 * - 写时删除：更新/删除/审核时主动删除缓存
 * - 缓存内容：Media 实体完整字段，供 MediaDetailResult、MediaListItem、UploadResult 等选择性填充
 * </p>
 *
 * @author aice
 * @since 2026-02-18
 */
public interface MediaRedisCacheService {

    /**
     * 媒体资源核心数据缓存 Key 前缀
     */
    String MEDIA_CORE_KEY_PREFIX = "media:core:";

    /**
     * 媒体资源缓存 TTL（秒）
     */
    int MEDIA_CORE_TTL_SECONDS = 600;

    /**
     * 从缓存获取媒体核心数据
     *
     * @param mediaId 媒体 ID
     * @return 命中返回 Media 实体，未命中返回 null
     */
    Media getMediaCore(Long mediaId);

    /**
     * 将媒体核心数据写入缓存
     *
     * @param mediaId 媒体 ID
     * @param media   Media 实体
     */
    void putMediaCore(Long mediaId, Media media);

    /**
     * 删除媒体核心数据缓存
     *
     * @param mediaId 媒体 ID
     */
    void evictMediaCore(Long mediaId);
}
