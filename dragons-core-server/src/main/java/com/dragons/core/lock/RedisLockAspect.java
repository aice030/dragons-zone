package com.dragons.core.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Redis 分布式锁切面：把锁生命周期从业务代码中抽离。
 *
 * 业务路径：
 * 1) 解析注解参数，定位锁维度（media:core / media:list）。
 * 2) 调用模板 prepare，完成“尝试加锁 + 启动 watchdog”。
 * 3) 将上下文放入 ThreadLocal，让业务回调按“拿到锁/没拿到锁”分支执行。
 * 4) finally 里清理上下文并统一释放锁资源。
 */
@Aspect
@Component
public class RedisLockAspect {

    private final RedisLockTemplate redisLockTemplate;

    public RedisLockAspect(RedisLockTemplate redisLockTemplate) {
        this.redisLockTemplate = redisLockTemplate;
    }

    @Around("@annotation(withRedisLock)")
    public Object around(ProceedingJoinPoint pjp, WithRedisLock withRedisLock) throws Throwable {
        RedisLockMeta meta = resolveMeta(withRedisLock, pjp.getArgs());
        RedisLockExecution execution = redisLockTemplate.prepare(withRedisLock, meta);

        RedisLockContextHolder.set(execution.getContext());
        try {
            return pjp.proceed();
        } finally {
            RedisLockContextHolder.clear();
            redisLockTemplate.finish(execution);
        }
    }

    private RedisLockMeta resolveMeta(WithRedisLock cfg, Object[] args) {
        if (cfg.type() == RedisLockType.MEDIA_CORE) {
            Long mediaId = castArg(args, cfg.mediaIdArg(), Long.class, "mediaIdArg");
            return RedisLockMeta.forMediaCore(mediaId);
        }

        if (cfg.type() == RedisLockType.MEDIA_LIST) {
            Long zoneUserId = castArg(args, cfg.zoneUserIdArg(), Long.class, "zoneUserIdArg");
            Byte category = castArg(args, cfg.categoryArg(), Byte.class, "categoryArg");
            Integer page = castArg(args, cfg.pageArg(), Integer.class, "pageArg");
            Integer size = castArg(args, cfg.sizeArg(), Integer.class, "sizeArg");
            return RedisLockMeta.forMediaList(zoneUserId, category, page, size);
        }

        throw new IllegalArgumentException("Unsupported lock type: " + cfg.type());
    }

    private <T> T castArg(Object[] args, int index, Class<T> type, String fieldName) {
        if (index < 0 || index >= args.length) {
            throw new IllegalArgumentException(fieldName + " index out of range: " + index);
        }
        Object value = args[index];
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(fieldName + " type mismatch, expected " + type.getSimpleName());
        }
        return type.cast(value);
    }
}
