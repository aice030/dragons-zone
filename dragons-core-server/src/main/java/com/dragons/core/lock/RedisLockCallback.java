package com.dragons.core.lock;

/**
 * 锁托管执行回调。
 */
@FunctionalInterface
public interface RedisLockCallback<T> {

    T execute(RedisLockContext context);
}
