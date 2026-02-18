package com.dragons.core.cache;

import com.dragons.core.service.IMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
    public IMediaService.MediaDetailResult get(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        String key = KEY_PREFIX + mediaId;
        try {
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
    public void put(Long mediaId, IMediaService.MediaDetailResult detail) {
        if (mediaId == null || detail == null) {
            return;
        }
        String key = KEY_PREFIX + mediaId;
        try {
            redisTemplate.opsForValue().set(key, detail, TTL_SECONDS, TimeUnit.SECONDS);
            log.info("media detail cache put mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media detail cache put failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public void evict(Long mediaId) {
        if (mediaId == null) {
            return;
        }
        String key = KEY_PREFIX + mediaId;
        try {
            redisTemplate.delete(key);
            log.info("media detail cache evict mediaId={}", mediaId);
        } catch (Exception e) {
            log.error("media detail cache evict failed mediaId={} error={}", mediaId, e.getMessage());
        }
    }

    @Override
    public void evictBatch(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        try {
            List<String> keys = mediaIds.stream()
                    .filter(id -> id != null)
                    .map(id -> KEY_PREFIX + id)
                    .toList();
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("media detail cache evict batch count={} mediaIds={}", keys.size(), mediaIds);
            }
        } catch (Exception e) {
            log.error("media detail cache evict batch failed mediaIds={} error={}", mediaIds, e.getMessage());
        }
    }
}
