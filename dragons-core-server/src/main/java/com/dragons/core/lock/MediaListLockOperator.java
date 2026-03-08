package com.dragons.core.lock;

import com.dragons.core.cache.RedisCacheMediaListService;
import org.springframework.stereotype.Component;

/**
 * media:list 锁适配器。
 */
@Component
public class MediaListLockOperator implements RedisLockOperator {

    private final RedisCacheMediaListService redisCacheMediaListService;

    public MediaListLockOperator(RedisCacheMediaListService redisCacheMediaListService) {
        this.redisCacheMediaListService = redisCacheMediaListService;
    }

    @Override
    public RedisLockType type() {
        return RedisLockType.MEDIA_LIST;
    }

    @Override
    public boolean tryLock(RedisLockMeta meta, String requestId) {
        return redisCacheMediaListService.tryLockMediaList(
                meta.getZoneUserId(),
                meta.getCategory(),
                meta.getPage(),
                meta.getSize(),
                requestId
        );
    }

    @Override
    public boolean renew(RedisLockMeta meta, String requestId) {
        return redisCacheMediaListService.renewLockMediaList(
                meta.getZoneUserId(),
                meta.getCategory(),
                meta.getPage(),
                meta.getSize(),
                requestId
        );
    }

    @Override
    public void unlock(RedisLockMeta meta, String requestId) {
        redisCacheMediaListService.unlockMediaList(
                meta.getZoneUserId(),
                meta.getCategory(),
                meta.getPage(),
                meta.getSize(),
                requestId
        );
    }
}
