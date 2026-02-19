package com.dragons.core.cache;

import com.dragons.core.service.IMediaService;

import java.util.List;

/**
 * 媒体详情 Redis 缓存服务
 * <p>
 * 按 Redis_DESIGN.md 设计：
 * - Key 格式：media:detail:{mediaId}
 * - TTL：600 秒（10 分钟）
 * - 写时删除：更新/删除/审核时主动删除缓存
 * </p>
 *
 * @author aice
 * @since 2026-02-18
 */
public interface MediaRedisCacheService {

    /**
     * 媒体资源详情缓存 Key 前缀
     */
    String MEDIA_DETAIL_KEY_PREFIX = "media:detail:";

    /**
     * 下载链接缓存 Key 前缀
     */
    String DOWNLOAD_URL_KEY_PREFIX = "media:downloadUrl:";

    /**
     * 媒体资源缓存 TTL（秒）
     */
    int MEDIA_DETAIL_TTL_SECONDS = 600;

    /**
     * 从缓存获取媒体详情
     *
     * @param mediaId 媒体 ID
     * @return 命中返回 MediaDetailResult，未命中返回 null
     */
    IMediaService.MediaDetailResult getMediaDetail(Long mediaId);

    /**
     * 将媒体详情写入缓存
     *
     * @param mediaId 媒体 ID
     * @param detail  媒体详情
     */
    void putMediaDetail(Long mediaId, IMediaService.MediaDetailResult detail);

    /**
     * 删除媒体详情缓存
     *
     * @param mediaId 媒体 ID
     */
    void evictMediaDetail(Long mediaId);

    /**
     * 从缓存获取下载链接（仅 state=0 会写入缓存，调用方仅在 state=0 时调用）
     *
     * @param mediaId 媒体 ID
     * @return 命中返回 URL，未命中返回 null
     */
    String getDownloadUrl(Long mediaId);

    /**
     * 将下载链接写入缓存（仅对 state=0 的媒体调用）
     *
     * @param mediaId     媒体 ID
     * @param downloadUrl 临时下载链接
     * @param ttlSeconds  下载链接剩余有效时间（秒），用于计算缓存 TTL = ttlSeconds - 60
     */
    void putDownloadUrl(Long mediaId, String downloadUrl, int ttlSeconds);

    /**
     * 删除下载链接缓存
     *
     * @param mediaId 媒体 ID
     */
    void evictDownloadUrl(Long mediaId);
}
