package com.dragons.core.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要进行数据库写入重试的方法。
 *
 * 参数值 <= 0 表示使用全局默认配置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DbWriteRetry {

    /** 最大尝试次数（包含首次调用）。 */
    int maxAttempts() default -1;

    /** 首次重试前的基础等待时间（毫秒）。 */
    long initialDelayMs() default -1;

    /** 退避等待的上限（毫秒），防止等待时间无限增大。 */
    long maxDelayMs() default -1;

    /** 指数退避倍率，例如 2.0 表示每次大约翻倍。 */
    double multiplier() default -1;

    /** 抖动比例（0-1），用于打散并发重试峰值。 */
    double jitterRatio() default -1;
}
