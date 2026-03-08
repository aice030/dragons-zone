package com.dragons.core.lock;

import org.springframework.stereotype.Component;

/**
 * 锁门面：业务层通过该门面执行“受锁托管”的代码块。
 *
 * 说明：
 * - 门面方法被 @WithRedisLock 标注，切面会在方法进入/退出时处理锁生命周期；
 * - 业务回调里可通过 context.isLockAcquired() 决定走持锁逻辑还是降级逻辑。
 */
@Component
public class RedisLockFacade {

    /**
     * 业务入口（media:core）：
     * 1) 切面根据 mediaId 处理锁生命周期
     * 2) 方法体只读取上下文并执行业务回调
     */
    @WithRedisLock(type = RedisLockType.MEDIA_CORE, mediaIdArg = 0)
    public <T> T withMediaCoreLock(Long mediaId, RedisLockCallback<T> callback) {
        RedisLockContext ctx = RedisLockContextHolder.getRequired();
        return callback.execute(ctx);
    }

    /**
     * 业务入口（media:list）：
     * 1) 切面根据 zone/category/page/size 处理锁生命周期
     * 2) 方法体只读取上下文并执行业务回调
     */
    @WithRedisLock(
            type = RedisLockType.MEDIA_LIST,
            zoneUserIdArg = 0,
            categoryArg = 1,
            pageArg = 2,
            sizeArg = 3
    )
    public <T> T withMediaListLock(Long zoneUserId,
                                   Byte category,
                                   Integer page,
                                   Integer size,
                                   RedisLockCallback<T> callback) {
        RedisLockContext ctx = RedisLockContextHolder.getRequired();
        return callback.execute(ctx);
    }
}
