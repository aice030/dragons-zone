package com.dragons.core.cache;

import com.dragons.core.dto.MediaListCacheValue;
import com.dragons.core.entity.Media;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 媒体核心数据 Redis 缓存服务实现
 *
 * @author aice
 * @since 2026-02-18
 */
@Slf4j
@Service
public class MediaRedisCacheServiceImpl implements MediaRedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public MediaRedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Media getMediaCore(Long mediaId) {
        // 参数校验
        if (mediaId == null) {
            return null;
        }
        // 生成缓存key
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            // 根据缓存key查询缓存
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Media) {
                log.info("media core cache hit mediaId={}", mediaId);
                return (Media) value;
            }
            // 识别空值标记（防止缓存穿透）
            if (value instanceof String && NULL_VALUE_MARKER.equals(value)) {
                log.info("media core null value cache hit mediaId={}", mediaId);
                return null;
            }
        } catch (Exception e) {
            log.error("media core cache get failed mediaId={} error={}", mediaId, e.getMessage());
        }
        return null;
    }

    @Override
    public void putMediaCore(Long mediaId, Media media) {
        if (mediaId == null || media == null) {
            return;
        }
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, media, MEDIA_CORE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media core cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media core cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public void evictMediaCore(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            redisTemplate.delete(key);
            log.info("media core cache evict mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media core cache evict failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public Map<Long, Media> batchGetMediaCore(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Media> result = new HashMap<>();
        for (Long mediaId : mediaIds) {
            if (mediaId == null) {
                continue;
            }
            Media media = getMediaCore(mediaId);
            if (media != null) {
                result.put(mediaId, media);
            }
        }
        return result;
    }

    /**
     * 将 category 转换为缓存 key 中的字符串
     * null -> "all", 0 -> "0", 1 -> "1"
     */
    private String categoryToString(Byte category) {
        return category == null ? "all" : String.valueOf(category);
    }

    /**
     * 构建媒体列表缓存 key
     * 格式：media:list:{zoneUserId}:{category}:{page}:{size}
     */
    private String buildMediaListKey(Long zoneUserId, Byte category, Integer page, Integer size) {
        long safeZoneUserId = zoneUserId == null ? 0L : zoneUserId;
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return MEDIA_LIST_KEY_PREFIX + safeZoneUserId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    /**
     * 构建媒体列表缓存 key 模式（用于批量删除）
     * 格式：media:list:{zoneUserId}:{category}:*
     */
    private String buildMediaListKeyPattern(Long zoneUserId, Byte category) {
        long safeZoneUserId = zoneUserId == null ? 0L : zoneUserId;
        String categoryStr = categoryToString(category);
        return MEDIA_LIST_KEY_PREFIX + safeZoneUserId + ":" + categoryStr + ":*";
    }

    @Override
    public MediaListCacheValue getMediaList(Long zoneUserId, Byte category, Integer page, Integer size) {
        String key = buildMediaListKey(zoneUserId, category, page, size);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof MediaListCacheValue) {
                MediaListCacheValue cacheValue = (MediaListCacheValue) value;
                log.info("media list cache hit zoneUserId={} category={} page={} size={} total={} count={}", 
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

    @Override
    public void putMediaList(Long zoneUserId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds) {
        if (mediaIds == null || total == null) {
            return;
        }
        String key = buildMediaListKey(zoneUserId, category, page, size);
        try {
            MediaListCacheValue cacheValue = new MediaListCacheValue(total, mediaIds);
            redisTemplate.opsForValue().set(key, cacheValue, MEDIA_LIST_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media list cache put zoneUserId={} category={} page={} size={} total={} count={}", 
                    zoneUserId, category, page, size, total, mediaIds.size());
        } catch (Exception e) {
            log.error("media list cache put failed zoneUserId={} category={} page={} size={} error={}", 
                    zoneUserId, category, page, size, e.getMessage());
        }
    }

    @Override
    public void evictMediaList(Long zoneUserId, Byte category) {
        String pattern = buildMediaListKeyPattern(zoneUserId, category);
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("media list cache evict zoneUserId={} category={} deletedKeys={}", 
                        zoneUserId, category, keys.size());
            }
        } catch (Exception e) {
            log.error("media list cache evict failed zoneUserId={} category={} error={}", 
                    zoneUserId, category, e.getMessage());
        }
    }

    /**
     * 构建我的上传列表缓存 key
     * 格式：media:my:{uploaderId}:{category}:{page}:{size}
     */
    private String buildMyUploadListKey(Long uploaderId, Byte category, Integer page, Integer size) {
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId cannot be null");
        }
        String categoryStr = categoryToString(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        return MEDIA_MY_KEY_PREFIX + uploaderId + ":" + categoryStr + ":" + safePage + ":" + safeSize;
    }

    /**
     * 构建我的上传列表缓存 key 模式（用于批量删除）
     * 格式：media:my:{uploaderId}:{category}:*
     */
    private String buildMyUploadListKeyPattern(Long uploaderId, Byte category) {
        if (uploaderId == null) {
            throw new IllegalArgumentException("uploaderId cannot be null");
        }
        String categoryStr = categoryToString(category);
        return MEDIA_MY_KEY_PREFIX + uploaderId + ":" + categoryStr + ":*";
    }

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
                log.info("my upload list cache hit uploaderId={} category={} page={} size={} total={} count={}", 
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

    @Override
    public void putMyUploadList(Long uploaderId, Byte category, Integer page, Integer size, Long total, List<Long> mediaIds) {
        if (uploaderId == null || mediaIds == null || total == null) {
            return;
        }
        String key = buildMyUploadListKey(uploaderId, category, page, size);
        try {
            MediaListCacheValue cacheValue = new MediaListCacheValue(total, mediaIds);
            redisTemplate.opsForValue().set(key, cacheValue, MEDIA_LIST_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("my upload list cache put uploaderId={} category={} page={} size={} total={} count={}", 
                    uploaderId, category, page, size, total, mediaIds.size());
        } catch (Exception e) {
            log.error("my upload list cache put failed uploaderId={} category={} page={} size={} error={}", 
                    uploaderId, category, page, size, e.getMessage());
        }
    }

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
                log.info("my upload list cache evict uploaderId={} category={} deletedKeys={}", 
                        uploaderId, category, keys.size());
            }
        } catch (Exception e) {
            log.error("my upload list cache evict failed uploaderId={} category={} error={}", 
                    uploaderId, category, e.getMessage());
        }
    }

    @Override
    public void putNullValue(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, NULL_VALUE_MARKER, NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media core null value cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media core null value cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public boolean tryLock(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = LOCK_KEY_PREFIX + mediaId;
        try {
            // 使用 SETNX 原子操作：SET lock:media:core:{mediaId} {requestId} EX 5 NX
            // requestId 作为锁的值，用于标识锁的持有者
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    requestId,  // 使用 requestId 作为锁的值
                    LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            if (Boolean.TRUE.equals(success)) {
                log.info("distributed lock acquired mediaId={} requestId={}", mediaId, requestId);
                return true;
            } else {
                log.debug("distributed lock already held by another request mediaId={}", mediaId);
                return false;
            }
        } catch (Exception e) {
            log.error("try lock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }

    @Override
    public void unlock(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = LOCK_KEY_PREFIX + mediaId;
        try {
            // Lua 脚本：原子性地检查并删除锁
            // 只有当锁的值等于 requestId 时才删除，防止误释放其他线程的锁
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            org.springframework.data.redis.core.script.DefaultRedisScript<Long> script = 
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    requestId
            );
            
            if (result != null && result > 0) {
                log.info("distributed lock released mediaId={} requestId={}", mediaId, requestId);
            } else {
                log.warn("distributed lock release failed: lock not held by this request mediaId={} requestId={}", mediaId, requestId);
            }
        } catch (Exception e) {
            log.error("unlock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
        }
    }

    @Override
    public boolean renewLock(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = LOCK_KEY_PREFIX + mediaId;
        try {
            // Lua 脚本：原子性地检查并续期锁
            // 只有当锁的值等于 requestId 时才续期，防止续期其他线程的锁
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "    return 0 " +
                "end";
            
            org.springframework.data.redis.core.script.DefaultRedisScript<Long> script = 
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    requestId,
                    String.valueOf(LOCK_TTL_SECONDS)
            );
            
            if (result != null && result > 0) {
                log.debug("distributed lock renewed mediaId={} requestId={}", mediaId, requestId);
                return true;
            } else {
                log.debug("distributed lock renew failed: lock not held by this request mediaId={} requestId={}", mediaId, requestId);
                return false;
            }
        } catch (Exception e) {
            log.error("renew lock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }
}
