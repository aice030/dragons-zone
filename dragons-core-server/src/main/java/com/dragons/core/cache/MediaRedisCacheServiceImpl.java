package com.dragons.core.cache;

import com.dragons.core.entity.Media;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
}
