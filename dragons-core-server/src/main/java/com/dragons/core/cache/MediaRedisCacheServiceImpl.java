package com.dragons.core.cache;

import com.dragons.core.service.IMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 媒体详情 Redis 缓存服务实现
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
    public IMediaService.MediaDetailResult getMediaDetail(Long mediaId) {
        // 参数校验
        if (mediaId == null) {
            return null;
        }
        // 生成缓存key
        String key = MEDIA_DETAIL_KEY_PREFIX + mediaId;
        try {
            // 根据缓存key查询缓存
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof IMediaService.MediaDetailResult) {
                log.info("media detail cache hit mediaId={}", mediaId);
                return (IMediaService.MediaDetailResult) value;
            }
        } catch (Exception e) {
            log.error("media detail cache get failed mediaId={} error={}", mediaId, e.getMessage());
        }
        return null;
    }

    @Override
    public void putMediaDetail(Long mediaId, IMediaService.MediaDetailResult detail) {
        if (mediaId == null || detail == null) {
            return;
        }
        String key = MEDIA_DETAIL_KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, detail, MEDIA_DETAIL_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media detail cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media detail cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public void evictMediaDetail(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String key = MEDIA_DETAIL_KEY_PREFIX + mediaId;
        try {
            redisTemplate.delete(key);
            log.info("media detail cache evict mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media detail cache evict failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public String getDownloadUrl(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        String key = DOWNLOAD_URL_KEY_PREFIX + mediaId;
        try {
            String value = (String) redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.info("downloadUrl cache hit mediaId={}", mediaId);
                return value;
            }
        } catch (Exception e) {
            log.error("downloadUrl cache get failed mediaId={} error={}", mediaId, e.getMessage());
        }
        return null;
    }

    @Override
    public void putDownloadUrl(Long mediaId, String downloadUrl, int ttlSeconds) {
        if (mediaId == null || downloadUrl == null || downloadUrl.isEmpty()) {
            return;
        }
        if (ttlSeconds <= 5) {
            return;
        }
        int ttl = Math.max(ttlSeconds - 60, 5);
        String key = DOWNLOAD_URL_KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, downloadUrl, ttl, TimeUnit.SECONDS);
            log.info("downloadUrl cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("downloadUrl cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public void evictDownloadUrl(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String key = DOWNLOAD_URL_KEY_PREFIX + mediaId;
        try {
            redisTemplate.delete(key);
            log.info("downloadUrl cache evict mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("downloadUrl cache evict failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }
}
