package com.dragons.core.lock;

import lombok.Getter;

import java.util.concurrent.ScheduledFuture;

/**
 * 一次锁托管执行期对象。
 */
@Getter
public class RedisLockExecution {

    private final RedisLockOperator operator;
    private final RedisLockMeta meta;
    private final RedisLockContext context;
    private final ScheduledFuture<?> renewFuture;

    public RedisLockExecution(RedisLockOperator operator,
                              RedisLockMeta meta,
                              RedisLockContext context,
                              ScheduledFuture<?> renewFuture) {
        this.operator = operator;
        this.meta = meta;
        this.context = context;
        this.renewFuture = renewFuture;
    }
}
