package com.dragons.core.cache;

import com.dragons.core.dto.MediaListCacheValue;

import java.util.List;

/**
 * 媒体列表 Redis 缓存服务（media:list、media:my）
 * <p>
 * 按 Redis_DESIGN.md：ID 列表缓存 + 防击穿分布式锁。
 * </p>
 *
 * @author aice
 * @since 2026-02-18
 */
public interface RedisCacheMediaListService {

    String MEDIA_LIST_KEY_PREFIX = "media:list:";
    String MEDIA_MY_KEY_PREFIX = "media:my:";
    int MEDIA_LIST_TTL_SECONDS = 300;
    String LOCK_MEDIA_LIST_KEY_PREFIX = "lock:media:list:";
    String LOCK_MEDIA_MY_KEY_PREFIX = "lock:media:my:";
    int LOCK_TTL_SECONDS = 5;

    /**
     * 从缓存获取媒体展示列表（公共区/专区）。Key 为 media:list:{zoneUserId}:{category}:{page}:{size}，未命中返回 null。
     */
    MediaListCacheValue getMediaList(Long zoneUserId, Byte category, Integer page, Integer size);

    /**
     * 将媒体展示列表（total + mediaIds）写入缓存，TTL 5 分钟。用于 listMedia 未命中时回填。
     */
    void putMediaList(Long zoneUserId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds);

    /**
     * 删除指定专区+分类下的所有分页列表缓存（按 pattern 批量删除）。媒体变更/审核/删除时调用。
     */
    void evictMediaList(Long zoneUserId, Byte category);

    /**
     * 从缓存获取「我的上传」列表。Key 为 media:my:{uploaderId}:{category}:{page}:{size}，未命中返回 null。
     */
    MediaListCacheValue getMyUploadList(Long uploaderId, Byte category, Integer page, Integer size);

    /**
     * 将「我的上传」列表（total + mediaIds）写入缓存，TTL 5 分钟。用于 listMyUpload 未命中时回填。
     */
    void putMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds);

    /**
     * 删除指定上传者+分类下的所有分页「我的上传」缓存。上传/删除等变更时调用。
     */
    void evictMyUploadList(Long uploaderId, Byte category);

    /**
     * 尝试获取媒体展示列表缓存的分布式锁（防击穿）。仅持有者可用 requestId 释放/续期。
     *
     * @return 获取成功返回 true，否则 false
     */
    boolean tryLockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId);

    /** 释放媒体展示列表分布式锁，仅 requestId 匹配时删除（Lua 原子）。 */
    void unlockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId);

    /** 续期媒体展示列表分布式锁，仅 requestId 匹配时延长 TTL（Lua 原子）。 */
    boolean renewLockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId);

    /**
     * 尝试获取「我的上传」列表缓存的分布式锁（防击穿）。仅持有者可用 requestId 释放/续期。
     *
     * @return 获取成功返回 true，否则 false
     */
    boolean tryLockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId);

    /** 释放「我的上传」列表分布式锁，仅 requestId 匹配时删除（Lua 原子）。 */
    void unlockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId);

    /** 续期「我的上传」列表分布式锁，仅 requestId 匹配时延长 TTL（Lua 原子）。 */
    boolean renewLockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId);
}
