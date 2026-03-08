package com.dragons.core.lock;

/**
 * 分布式锁类型。
 *
 * 说明：
 * - MEDIA_CORE：单条 media:core 防击穿锁
 * - MEDIA_LIST：media:list 列表防击穿锁
 */
public enum RedisLockType {
    MEDIA_CORE,
    MEDIA_LIST
}
