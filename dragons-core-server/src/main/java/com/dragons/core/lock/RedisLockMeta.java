package com.dragons.core.lock;

import lombok.Getter;

/**
 * 锁元数据：描述本次加锁所需的业务维度参数。
 */
@Getter
public class RedisLockMeta {

    private final RedisLockType type;
    private final Long mediaId;
    private final Long zoneUserId;
    private final Byte category;
    private final Integer page;
    private final Integer size;

    private RedisLockMeta(RedisLockType type,
                          Long mediaId,
                          Long zoneUserId,
                          Byte category,
                          Integer page,
                          Integer size) {
        this.type = type;
        this.mediaId = mediaId;
        this.zoneUserId = zoneUserId;
        this.category = category;
        this.page = page;
        this.size = size;
    }

    public static RedisLockMeta forMediaCore(Long mediaId) {
        return new RedisLockMeta(RedisLockType.MEDIA_CORE, mediaId, null, null, null, null);
    }

    public static RedisLockMeta forMediaList(Long zoneUserId, Byte category, Integer page, Integer size) {
        return new RedisLockMeta(RedisLockType.MEDIA_LIST, null, zoneUserId, category, page, size);
    }
}
