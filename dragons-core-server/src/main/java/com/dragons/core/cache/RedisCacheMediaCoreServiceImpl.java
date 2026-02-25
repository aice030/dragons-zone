package com.dragons.core.cache;

import com.dragons.core.entity.Media;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 媒体核心数据 Redis 缓存服务实现（media:core）。
 * 负责单条媒体缓存读写、空值缓存（防穿透）、以及 media:core 查询时的分布式锁（防击穿）。
 *
 * @author aice
 * @since 2026-02-18
 */
@Slf4j
@Service
public class RedisCacheMediaCoreServiceImpl implements RedisCacheMediaCoreService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheMediaCoreServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从缓存获取媒体核心数据。命中返回 Media；若为空值缓存（防穿透）则返回 null；未命中或异常也返回 null。
     */
    @Override
    public Media getMediaCore(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Media) {
                log.info("media core cache hit mediaId={}", mediaId);
                return (Media) value;
            }
            if (value instanceof String && NULL_VALUE_MARKER.equals(value)) {
                log.info("media core null value cache hit mediaId={}", mediaId);
                return null;
            }
        } catch (Exception e) {
            log.error("media core cache get failed mediaId={} error={}", mediaId, e.getMessage());
        }
        return null;
    }

    /**
     * 将媒体核心数据写入缓存，TTL 为 600 秒（10 分钟）。
     * 写入时去掉 likeCount/likeCountUpdateTime，点赞数统一由 ZSET 提供，避免冗余。
     */
    @Override
    public void putMediaCore(Long mediaId, Media media) {
        if (mediaId == null || media == null) {
            return;
        }
        Media toCache = copyMediaWithoutLikeCount(media);
        String key = MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, toCache, MEDIA_CORE_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media core cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media core cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    /** 复制 Media 并清空 likeCount/likeCountUpdateTime，用于写入 media:core 时避免冗余存储 */
    private static Media copyMediaWithoutLikeCount(Media source) {
        Media copy = new Media();
        copy.setId(source.getId());
        copy.setUploaderId(source.getUploaderId());
        copy.setFileHash(source.getFileHash());
        copy.setCategory(source.getCategory());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setStoragePath(source.getStoragePath());
        copy.setCoverPath(source.getCoverPath());
        copy.setCoverStatus(source.getCoverStatus());
        copy.setState(source.getState());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setCoverUrl(source.getCoverUrl());
        return copy;
    }

    /**
     * 删除指定媒体的核心数据缓存（写时删除，用于更新/删除/审核等变更场景）。
     */
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

    /**
     * 批量获取媒体核心数据。返回 mediaId -> Media 映射，未命中的 ID 不在结果中。
     */
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
     * 写入空值缓存（防穿透）：当 DB 查询不到该媒体时写入标记，短 TTL，避免恶意请求持续打穿到 DB。
     */
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

    /**
     * 尝试获取 media:core 的分布式锁（防击穿）。使用 SET key requestId EX TTL NX，仅当 requestId 一致时才能释放/续期。
     *
     * @return 获取成功返回 true，已被占用或异常返回 false
     */
    @Override
    public boolean tryLockMediaCore(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = LOCK_MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, requestId, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.info("distributed lock acquired mediaId={} requestId={}", mediaId, requestId);
                return true;
            }
            log.warn("distributed lock already held by another request mediaId={}", mediaId);
            return false;
        } catch (Exception e) {
            log.error("try lock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }

    /**
     * 释放 media:core 分布式锁。仅当 Redis 中锁的值等于 requestId 时才删除（Lua 保证原子性），防止误释其他线程的锁。
     */
    @Override
    public void unlockMediaCore(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String key = LOCK_MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            String luaScript =
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "    return redis.call('del', KEYS[1]) " +
                            "else " +
                            "    return 0 " +
                            "end";
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), requestId);
            if (result != null && result > 0) {
                log.info("distributed lock released mediaId={} requestId={}", mediaId, requestId);
            } else {
                log.warn("distributed lock release failed: lock not held by this request mediaId={} requestId={}", mediaId, requestId);
            }
        } catch (Exception e) {
            log.error("unlock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
        }
    }

    /**
     * 续期 media:core 分布式锁。仅当锁的值等于 requestId 时才延长 TTL（Lua 原子），用于 WatchDog 防止加载 DB 时锁过期。
     *
     * @return 续期成功返回 true，否则 false
     */
    @Override
    public boolean renewLockMediaCore(Long mediaId, String requestId) {
        if (mediaId == null || requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        String key = LOCK_MEDIA_CORE_KEY_PREFIX + mediaId;
        try {
            String luaScript =
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                            "else " +
                            "    return 0 " +
                            "end";
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(luaScript);
            script.setResultType(Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), requestId, String.valueOf(LOCK_TTL_SECONDS));
            if (result != null && result > 0) {
                log.debug("distributed lock renewed mediaId={} requestId={}", mediaId, requestId);
                return true;
            }
            log.debug("distributed lock renew failed: lock not held by this request mediaId={} requestId={}", mediaId, requestId);
            return false;
        } catch (Exception e) {
            log.error("renew lock failed mediaId={} requestId={} error={}", mediaId, requestId, e.getMessage());
            return false;
        }
    }
}
