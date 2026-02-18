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
     * 缓存 Key 前缀
     */
    String KEY_PREFIX = "media:detail:";

    /**
     * 缓存 TTL（秒）
     */
    int TTL_SECONDS = 600;

    /**
     * 从缓存获取媒体详情
     *
     * @param mediaId 媒体 ID
     * @return 命中返回 MediaDetailResult，未命中返回 null
     */
    IMediaService.MediaDetailResult get(Long mediaId);

    /**
     * 写入缓存
     *
     * @param mediaId 媒体 ID
     * @param detail  媒体详情
     */
    void put(Long mediaId, IMediaService.MediaDetailResult detail);

    /**
     * 删除缓存（单个）
     *
     * @param mediaId 媒体 ID
     */
    void evict(Long mediaId);

    /**
     * 批量删除缓存
     *
     * @param mediaIds 媒体 ID 列表
     */
    void evictBatch(List<Long> mediaIds);
}
