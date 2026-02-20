package com.dragons.core.cache;

import com.dragons.core.entity.Media;

import java.util.List;
import java.util.Map;

/**
 * 媒体核心数据 Redis 缓存服务（media:core）
 * <p>
 * 按 Redis_DESIGN.md：Key media:core:{mediaId}，TTL 600s，写时删除；含空值缓存与防击穿锁。
 * </p>
 *
 * @author aice
 * @since 2026-02-18
 */
public interface RedisCacheMediaCoreService {

    String MEDIA_CORE_KEY_PREFIX = "media:core:";
    int MEDIA_CORE_TTL_SECONDS = 600;
    String NULL_VALUE_MARKER = "__NULL__";
    int NULL_VALUE_TTL_SECONDS = 60;
    String LOCK_MEDIA_CORE_KEY_PREFIX = "lock:media:core:";
    int LOCK_TTL_SECONDS = 5;

    /**
     * 从缓存获取媒体核心数据。命中返回 Media；若为空值缓存（防穿透）则返回 null；未命中或异常也返回 null。
     */
    Media getMediaCore(Long mediaId);

    /**
     * 将媒体核心数据写入缓存，TTL 为 600 秒（10 分钟）。
     */
    void putMediaCore(Long mediaId, Media media);

    /**
     * 删除指定媒体的核心数据缓存（写时删除，用于更新/删除/审核等变更场景）。
     */
    void evictMediaCore(Long mediaId);

    /**
     * 批量获取媒体核心数据。返回 mediaId -> Media 映射，未命中的 ID 不在结果中。
     */
    Map<Long, Media> batchGetMediaCore(List<Long> mediaIds);

    /**
     * 写入空值缓存（防穿透）：当 DB 查询不到该媒体时写入标记，短 TTL，避免恶意请求持续打穿到 DB。
     */
    void putNullValue(Long mediaId);

    /**
     * 尝试获取 media:core 的分布式锁（防击穿）。使用 SET key requestId EX TTL NX，仅当 requestId 一致时才能释放/续期。
     *
     * @return 获取成功返回 true，已被占用或异常返回 false
     */
    boolean tryLockMediaCore(Long mediaId, String requestId);

    /**
     * 释放 media:core 分布式锁。仅当 Redis 中锁的值等于 requestId 时才删除（Lua 保证原子性），防止误释其他线程的锁。
     */
    void unlockMediaCore(Long mediaId, String requestId);

    /**
     * 续期 media:core 分布式锁。仅当锁的值等于 requestId 时才延长 TTL（Lua 原子），用于 WatchDog 防止加载 DB 时锁过期。
     *
     * @return 续期成功返回 true，否则 false
     */
    boolean renewLockMediaCore(Long mediaId, String requestId);
}
