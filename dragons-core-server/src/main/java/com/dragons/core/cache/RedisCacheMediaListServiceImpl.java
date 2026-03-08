package com.dragons.core.cache;

import com.dragons.core.dto.MediaListCacheValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 媒体列表 Redis 缓存服务实现（media:list、media:my）。
 * 负责公共区/专区列表、我的上传列表的 ID 列表缓存读写，以及对应查询时的分布式锁（防击穿）。
 *
 * @author aice
 * @since 2026-02-18
 */
@Slf4j
@Service
public class RedisCacheMediaListServiceImpl implements RedisCacheMediaListService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheMediaListServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // category 转缓存 key 片段：null -> "all", 0 -> "0", 1 -> "1"
    private static String categoryToString(Byte category) {
        return category == null ? "all" : String.valueOf(category);
    }

    // 构建 media:list 缓存 key：media:list:{zoneUserId}:{category}:{page}:{size}
    private static String buildMediaListKey(Long zoneUserId, Byte category, Integer page, Integer size) {
        long safeZoneUserId = zoneUserId == null ? 0L : zoneUserId;
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return MEDIA_LIST_KEY_PREFIX + safeZoneUserId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    // 构建 media:list 批量删除用 pattern：media:list:{zoneUserId}:{category}:
    private static String buildMediaListKeyPattern(Long zoneUserId, Byte category) {
        long safeZoneUserId = zoneUserId == null ? 0L : zoneUserId;
        return MEDIA_LIST_KEY_PREFIX + safeZoneUserId + ":" + categoryToString(category) + ":*";
    }

    // 构建 media:list 分布式锁 key
    private static String buildMediaListLockKey(Long zoneUserId, Byte category, Integer page, Integer size) {
        long safeZoneUserId = zoneUserId == null ? 0L : zoneUserId;
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return LOCK_MEDIA_LIST_KEY_PREFIX + safeZoneUserId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    // 构建 media:my 缓存 key：media:my:{uploaderId}:{category}:{page}:{size}
    private static String buildMyUploadListKey(Long uploaderId, Byte category, Integer page, Integer size) {
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId cannot be null");
        }
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return MEDIA_MY_KEY_PREFIX + uploaderId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    // 构建 media:my 批量删除用 pattern
    private static String buildMyUploadListKeyPattern(Long uploaderId, Byte category) {
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId cannot be null");
        }
        return MEDIA_MY_KEY_PREFIX + uploaderId + ":" + categoryToString(category) + ":*";
    }

    // 构建 media:my 分布式锁 key
    private static String buildMyUploadListLockKey(Long uploaderId, Byte category, Integer page, Integer size) {
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId cannot be null");
        }
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return LOCK_MEDIA_MY_KEY_PREFIX + uploaderId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    /**
     * 从缓存获取媒体展示列表（公共区/专区）。Key 为 media:list:{zoneUserId}:{category}:{page}:{size}，未命中返回 null。
     */
    @Override
    public MediaListCacheValue getMediaList(Long zoneUserId, Byte category, Integer page, Integer size) {
        String key = buildMediaListKey(zoneUserId, category, page, size);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof MediaListCacheValue) {
                MediaListCacheValue cacheValue = (MediaListCacheValue) value;
                log.debug("media list cache hit zoneUserId={} category={} page={} size={} total={} count={}",
                        zoneUserId, category, page, size, cacheValue.getTotal(),
                        cacheValue.getMediaIds() != null ? cacheValue.getMediaIds().size() : 0);
                return cacheValue;
            }
        } catch (Exception e) {
            log.error("media list cache get failed zoneUserId={} category={} page={} size={} error={}",
                    zoneUserId, category, page, size, e.getMessage());
        }
        return null;
    }

    /**
     * 将媒体展示列表（total + mediaIds）写入缓存，TTL 5 分钟。用于 listMedia 未命中时回填。
     */
    @Override
    public void putMediaList(Long zoneUserId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds) {
        if (mediaIds == null || total == null) {
            return;
        }
        String key = buildMediaListKey(zoneUserId, category, page, size);
        try {
            MediaListCacheValue cacheValue = new MediaListCacheValue(total, mediaIds);
            redisTemplate.opsForValue().set(key, cacheValue, MEDIA_LIST_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("media list cache put zoneUserId={} category={} page={} size={} total={} count={}",
                    zoneUserId, category, page, size, total, mediaIds.size());
        } catch (Exception e) {
            log.error("media list cache put failed zoneUserId={} category={} page={} size={} error={}",
                    zoneUserId, category, page, size, e.getMessage());
        }
    }

    /**
     * 删除指定专区+分类下的所有分页列表缓存（按 pattern 批量删除）。媒体变更/审核/删除时调用。
     */
    @Override
    public void evictMediaList(Long zoneUserId, Byte category) {
        String pattern = buildMediaListKeyPattern(zoneUserId, category);
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("media list cache evict zoneUserId={} category={} deletedKeys={}", zoneUserId, category, keys.size());
            }
        } catch (Exception e) {
            log.error("media list cache evict failed zoneUserId={} category={} error={}", zoneUserId, category, e.getMessage());
        }
    }

    /**
     * 从缓存获取「我的上传」列表。Key 为 media:my:{uploaderId}:{category}:{page}:{size}，未命中返回 null。
     */
    @Override
    public MediaListCacheValue getMyUploadList(Long uploaderId, Byte category, Integer page, Integer size) {
        if (uploaderId == null) {
            return null;
        }
        String key = buildMyUploadListKey(uploaderId, category, page, size);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof MediaListCacheValue) {
                MediaListCacheValue cacheValue = (MediaListCacheValue) value;
                log.debug("my upload list cache hit uploaderId={} category={} page={} size={} total={} count={}",
                        uploaderId, category, page, size, cacheValue.getTotal(),
                        cacheValue.getMediaIds() != null ? cacheValue.getMediaIds().size() : 0);
                return cacheValue;
            }
        } catch (Exception e) {
            log.error("my upload list cache get failed uploaderId={} category={} page={} size={} error={}",
                    uploaderId, category, page, size, e.getMessage());
        }
        return null;
    }

    /**
     * 将「我的上传」列表（total + mediaIds）写入缓存，TTL 5 分钟。用于 listMyUpload 未命中时回填。
     */
    @Override
    public void putMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds) {
        if (uploaderId == null || mediaIds == null || total == null) {
            return;
        }
        String key = buildMyUploadListKey(uploaderId, category, page, size);
        try {
            MediaListCacheValue cacheValue = new MediaListCacheValue(total, mediaIds);
            redisTemplate.opsForValue().set(key, cacheValue, MEDIA_LIST_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("my upload list cache put uploaderId={} category={} page={} size={} total={} count={}",
                    uploaderId, category, page, size, total, mediaIds.size());
        } catch (Exception e) {
            log.error("my upload list cache put failed uploaderId={} category={} page={} size={} error={}",
                    uploaderId, category, page, size, e.getMessage());
        }
    }

    /**
     * 删除指定上传者+分类下的所有分页「我的上传」缓存。上传/删除等变更时调用。
     */
    @Override
    public void evictMyUploadList(Long uploaderId, Byte category) {
        if (uploaderId == null) {
            return;
        }
        String pattern = buildMyUploadListKeyPattern(uploaderId, category);
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("my upload list cache evict uploaderId={} category={} deletedKeys={}", uploaderId, category, keys.size());
            }
        } catch (Exception e) {
            log.error("my upload list cache evict failed uploaderId={} category={} error={}", uploaderId, category, e.getMessage());
        }
    }

    private static final String LUA_UNLOCK = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final String LUA_RENEW = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end";

    /**
     * 尝试获取媒体展示列表缓存的分布式锁（防击穿）。仅持有者可用 requestId 释放/续期。
     *
     * @return 获取成功返回 true，否则 false
     */
    @Override
    public boolean tryLockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildMediaListLockKey(zoneUserId, category, page, size);
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, requestId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.debug("distributed lock acquired for media list zoneUserId={} category={} page={} size={} requestId={}",
                        zoneUserId, category, page, size, requestId);
                return true;
            }
            log.warn("distributed lock already held zoneUserId={} category={} page={} size={}", zoneUserId, category, page, size);
            return false;
        } catch (Exception e) {
            log.error("try lock failed for media list zoneUserId={} category={} page={} size={} requestId={} error={}",
                    zoneUserId, category, page, size, requestId, e.getMessage());
            return false;
        }
    }

    /** 释放媒体展示列表分布式锁，仅 requestId 匹配时删除（Lua 原子）。 */
    @Override
    public void unlockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = buildMediaListLockKey(zoneUserId, category, page, size);
        executeUnlock(key, requestId, "media list", zoneUserId + " " + category + " " + page + " " + size);
    }

    /** 续期媒体展示列表分布式锁，仅 requestId 匹配时延长 TTL（Lua 原子）。 */
    @Override
    public boolean renewLockMediaList(Long zoneUserId, Byte category, Integer page, Integer size, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildMediaListLockKey(zoneUserId, category, page, size);
        return executeRenew(key, requestId);
    }

    /**
     * 尝试获取「我的上传」列表缓存的分布式锁（防击穿）。仅持有者可用 requestId 释放/续期。
     *
     * @return 获取成功返回 true，否则 false
     */
    @Override
    public boolean tryLockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId) {
        if (uploaderId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildMyUploadListLockKey(uploaderId, category, page, size);
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, requestId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.debug("distributed lock acquired for my upload list uploaderId={} category={} page={} size={} requestId={}",
                        uploaderId, category, page, size, requestId);
                return true;
            }
            log.warn("distributed lock already held uploaderId={} category={} page={} size={}", uploaderId, category, page, size);
            return false;
        } catch (Exception e) {
            log.error("try lock failed for my upload list uploaderId={} category={} page={} size={} requestId={} error={}",
                    uploaderId, category, page, size, requestId, e.getMessage());
            return false;
        }
    }

    /** 释放「我的上传」列表分布式锁，仅 requestId 匹配时删除（Lua 原子）。 */
    @Override
    public void unlockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId) {
        if (uploaderId == null || requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = buildMyUploadListLockKey(uploaderId, category, page, size);
        executeUnlock(key, requestId, "my upload list", uploaderId + " " + category + " " + page + " " + size);
    }

    /** 续期「我的上传」列表分布式锁，仅 requestId 匹配时延长 TTL（Lua 原子）。 */
    @Override
    public boolean renewLockMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, String requestId) {
        if (uploaderId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = buildMyUploadListLockKey(uploaderId, category, page, size);
        return executeRenew(key, requestId);
    }

    /** 通用释放锁：Lua 脚本仅当 value==requestId 时 DEL，避免误释他人锁。 */
    private void executeUnlock(String key, String requestId, String label, Object logKey) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_UNLOCK);
            script.setResultType(Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), requestId);
            if (result != null && result > 0) {
                log.debug("distributed lock released for {} {}", label, logKey);
            } else {
                log.warn("distributed lock release failed: lock not held by this request {} {}", label, logKey);
            }
        } catch (Exception e) {
            log.error("unlock failed for {} {} error={}", label, logKey, e.getMessage());
        }
    }

    /** 通用续期锁：Lua 脚本仅当 value==requestId 时 EXPIRE。 */
    private boolean executeRenew(String key, String requestId) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_RENEW);
            script.setResultType(Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), requestId, String.valueOf(LOCK_TTL_SECONDS));
            if (result != null && result > 0) {
                log.debug("distributed lock renewed key={}", key);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("renew lock failed key={} error={}", key, e.getMessage());
            return false;
        }
    }
}
