package com.dragons.core.lock;

import com.dragons.core.cache.RedisCacheMediaCoreService;
import org.springframework.stereotype.Component;

/**
 * media:core 锁适配器。
 */
@Component
public class MediaCoreLockOperator implements RedisLockOperator {

    private final RedisCacheMediaCoreService redisCacheMediaCoreService;

    public MediaCoreLockOperator(RedisCacheMediaCoreService redisCacheMediaCoreService) {
        this.redisCacheMediaCoreService = redisCacheMediaCoreService;
    }

    @Override
    public RedisLockType type() {
        return RedisLockType.MEDIA_CORE;
    }

    @Override
    public boolean tryLock(RedisLockMeta meta, String requestId) {
        return redisCacheMediaCoreService.tryLockMediaCore(meta.getMediaId(), requestId);
    }

    @Override
    public boolean renew(RedisLockMeta meta, String requestId) {
        return redisCacheMediaCoreService.renewLockMediaCore(meta.getMediaId(), requestId);
    }

    @Override
    public void unlock(RedisLockMeta meta, String requestId) {
        redisCacheMediaCoreService.unlockMediaCore(meta.getMediaId(), requestId);
    }
}
