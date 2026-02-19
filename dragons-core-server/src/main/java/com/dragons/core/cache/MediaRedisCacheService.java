package com.dragons.core.cache;

import com.dragons.core.dto.MediaListCacheValue;
import com.dragons.core.entity.Media;

import java.util.List;
import java.util.Map;

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
     * 空值缓存标记（用于防止缓存穿透）
     */
    String NULL_VALUE_MARKER = "__NULL__";

    /**
     * 空值缓存 TTL（秒）
     */
    int NULL_VALUE_TTL_SECONDS = 60;

    /**
     * 分布式锁 Key 前缀（用于防止缓存击穿）
     */
    String LOCK_KEY_PREFIX = "lock:media:core:";

    /**
     * 分布式锁 TTL（秒）
     */
    int LOCK_TTL_SECONDS = 5;

    /**
     * 媒体列表缓存 Key 前缀
     */
    String MEDIA_LIST_KEY_PREFIX = "media:list:";

    /**
     * 我的上传列表缓存 Key 前缀
     */
    String MEDIA_MY_KEY_PREFIX = "media:my:";

    /**
     * 媒体列表缓存 TTL（秒）
     */
    int MEDIA_LIST_TTL_SECONDS = 300;

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

    /**
     * 批量获取媒体核心数据
     *
     * @param mediaIds 媒体 ID 列表
     * @return mediaId -> Media 的映射，未命中的ID不在返回结果中
     */
    Map<Long, Media> batchGetMediaCore(List<Long> mediaIds);

    /**
     * 从缓存获取媒体列表（包含total和mediaIds）
     *
     * @param zoneUserId 专区ID：0=公共区，其他=成员专区ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 命中返回包含total和mediaIds的缓存值，未命中返回 null
     */
    MediaListCacheValue getMediaList(Long zoneUserId, Byte category, Integer page, Integer size);

    /**
     * 将媒体列表（包含total和mediaIds）写入缓存
     *
     * @param zoneUserId 专区ID：0=公共区，其他=成员专区ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @param total 列表总数
     * @param mediaIds 媒体ID列表
     */
    void putMediaList(Long zoneUserId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds);

    /**
     * 删除媒体列表缓存（删除指定 zoneUserId 和 category 的所有分页缓存）
     *
     * @param zoneUserId 专区ID：0=公共区，其他=成员专区ID
     * @param category 分类：null=all，0=图片，1=视频
     */
    void evictMediaList(Long zoneUserId, Byte category);

    /**
     * 从缓存获取我的上传列表（包含total和mediaIds）
     *
     * @param uploaderId 上传者用户ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 命中返回包含total和mediaIds的缓存值，未命中返回 null
     */
    MediaListCacheValue getMyUploadList(Long uploaderId, Byte category, Integer page, Integer size);

    /**
     * 将我的上传列表（包含total和mediaIds）写入缓存
     *
     * @param uploaderId 上传者用户ID
     * @param category 分类：null=all，0=图片，1=视频
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @param total 列表总数
     * @param mediaIds 媒体ID列表
     */
    void putMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds);

    /**
     * 删除我的上传列表缓存（删除指定 uploaderId 和 category 的所有分页缓存）
     *
     * @param uploaderId 上传者用户ID
     * @param category 分类：null=all，0=图片，1=视频
     */
    void evictMyUploadList(Long uploaderId, Byte category);

    /**
     * 写入空值缓存（防止缓存穿透）
     *
     * @param mediaId 媒体 ID
     */
    void putNullValue(Long mediaId);

    /**
     * 尝试获取分布式锁（用于防止缓存击穿）
     *
     * @param mediaId 媒体 ID
     * @param requestId 请求唯一标识（用于防止误释放其他线程的锁）
     * @return 获取成功返回 true，失败返回 false
     */
    boolean tryLock(Long mediaId, String requestId);

    /**
     * 释放分布式锁（只有 requestId 匹配时才释放）
     *
     * @param mediaId 媒体 ID
     * @param requestId 请求唯一标识（必须与获取锁时的 requestId 一致）
     */
    void unlock(Long mediaId, String requestId);

    /**
     * 续期分布式锁（延长锁的过期时间，只有 requestId 匹配时才续期）
     *
     * @param mediaId 媒体 ID
     * @param requestId 请求唯一标识（必须与获取锁时的 requestId 一致）
     * @return 续期成功返回 true，失败返回 false
     */
    boolean renewLock(Long mediaId, String requestId);
}
