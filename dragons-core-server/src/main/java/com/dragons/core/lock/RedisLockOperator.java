package com.dragons.core.lock;

/**
 * Redis 分布式锁操作抽象。
 *
 * 不同业务锁（media:core / media:list）通过不同实现接入。
 */
public interface RedisLockOperator {

    RedisLockType type();

    boolean tryLock(RedisLockMeta meta, String requestId);

    boolean renew(RedisLockMeta meta, String requestId);

    void unlock(RedisLockMeta meta, String requestId);
}
