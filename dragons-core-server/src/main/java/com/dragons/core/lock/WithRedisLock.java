package com.dragons.core.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为方法声明“由切面托管 Redis 分布式锁生命周期”。
 *
 * 切面负责：
 * 1) 获取锁（含重试）
 * 2) 启动 watchdog 续期
 * 3) finally 中停止续期并释放锁
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithRedisLock {

    RedisLockType type();

    /** 锁获取重试次数。 */
    int acquireRetries() default 3;

    /** 获取失败后的重试间隔（毫秒）。 */
    long retrySleepMs() default 100L;

    /**
     * watchdog 续期间隔（毫秒）。
     * 小于等于 0 时由全局配置基于锁 TTL 推导。
     */
    long renewPeriodMs() default -1L;

    /** type=MEDIA_CORE 时，mediaId 在参数列表中的下标。 */
    int mediaIdArg() default -1;

    /** type=MEDIA_LIST 时，zoneUserId 在参数列表中的下标。 */
    int zoneUserIdArg() default -1;

    /** type=MEDIA_LIST 时，category 在参数列表中的下标。 */
    int categoryArg() default -1;

    /** type=MEDIA_LIST 时，page 在参数列表中的下标。 */
    int pageArg() default -1;

    /** type=MEDIA_LIST 时，size 在参数列表中的下标。 */
    int sizeArg() default -1;
}
