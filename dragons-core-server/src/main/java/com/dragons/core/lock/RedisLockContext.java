package com.dragons.core.lock;

import lombok.Getter;

/**
 * 当前线程的锁执行上下文。
 *
 * 业务代码可通过该对象判断：当前是否拿到锁，进而决定走“持锁分支”还是“降级分支”。
 */
@Getter
public class RedisLockContext {

    private final RedisLockType type;
    private final String requestId;
    private final boolean lockAcquired;
    private final Long mediaId;
    private final Long zoneUserId;
    private final Byte category;
    private final Integer page;
    private final Integer size;

    public RedisLockContext(RedisLockType type,
                            String requestId,
                            boolean lockAcquired,
                            Long mediaId,
                            Long zoneUserId,
                            Byte category,
                            Integer page,
                            Integer size) {
        this.type = type;
        this.requestId = requestId;
        this.lockAcquired = lockAcquired;
        this.mediaId = mediaId;
        this.zoneUserId = zoneUserId;
        this.category = category;
        this.page = page;
        this.size = size;
    }
}
