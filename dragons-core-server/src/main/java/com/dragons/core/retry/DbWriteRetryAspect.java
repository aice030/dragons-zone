package com.dragons.core.retry;

import com.dragons.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DB 写入重试切面。
 *
 * 只负责“是否重试、重试几次、间隔多久”，不处理业务返回码映射。
 */
@Slf4j
@Aspect
@Component
public class DbWriteRetryAspect {

    private final DbRetryProperties properties;

    public DbWriteRetryAspect(DbRetryProperties properties) {
        this.properties = properties;
    }

    @Around("@annotation(dbWriteRetry)")
    public Object around(ProceedingJoinPoint pjp, DbWriteRetry dbWriteRetry) throws Throwable {
        // 注解优先，未设置则使用全局默认配置
        int maxAttempts = resolveInt(dbWriteRetry.maxAttempts(), properties.getMaxAttempts(), 1);
        long initialDelayMs = resolveLong(dbWriteRetry.initialDelayMs(), properties.getInitialDelayMs(), 0L);
        long maxDelayMs = resolveLong(dbWriteRetry.maxDelayMs(), properties.getMaxDelayMs(), initialDelayMs);
        double multiplier = resolveDouble(dbWriteRetry.multiplier(), properties.getMultiplier(), 1.0d);
        double jitterRatio = resolveDouble(dbWriteRetry.jitterRatio(), properties.getJitterRatio(), 0.0d);

        Throwable last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (Throwable ex) {
                // 非可重试异常直接抛出，避免误重试业务错误
                if (!isRetryable(ex)) {
                    throw ex;
                }
                last = ex;
                if (attempt >= maxAttempts) {
                    break;
                }

                long sleepMs = computeBackoff(attempt, initialDelayMs, maxDelayMs, multiplier, jitterRatio);
                log.warn("db write retry scheduled method={} attempt={}/{} delayMs={} error={}",
                        pjp.getSignature().toShortString(), attempt, maxAttempts, sleepMs, ex.getMessage());
                sleepQuietly(sleepMs);
            }
        }

        throw last;
    }

    private boolean isRetryable(Throwable ex) {
        // 业务异常不应重试，避免重复执行业务分支
        if (ex instanceof BusinessException) {
            return false;
        }
        // 对“写入返回 false”场景，统一按可重试处理
        if (ex instanceof DbWriteReturnedFalseException) {
            return true;
        }

        // 向上遍历 cause 链，识别底层数据库瞬时异常
        // PessimisticLockingFailureException 为 Spring 6 推荐替代（死锁/加锁失败）；保留 DeadlockLoserDataAccessException 以兼容旧版本抛出
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof DeadlockLoserDataAccessException
                    || cursor instanceof PessimisticLockingFailureException
                    || cursor instanceof CannotAcquireLockException
                    || cursor instanceof TransientDataAccessException
                    || cursor instanceof SQLTransientException
                    || cursor instanceof SQLRecoverableException) {
                return true;
            }
            cursor = cursor.getCause();
        }

        return false;
    }

    private long computeBackoff(int attempt,
                                long initialDelayMs,
                                long maxDelayMs,
                                double multiplier,
                                double jitterRatio) {
        // 先按指数退避计算基础等待时间
        long delay = initialDelayMs;
        for (int i = 1; i < attempt; i++) {
            delay = (long) Math.min(maxDelayMs, Math.max(0L, delay * multiplier));
        }

        // 无抖动直接返回
        if (jitterRatio <= 0) {
            return delay;
        }

        // 加抖动，减少多个线程同一时刻重试造成的“惊群”
        double ratio = ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
        long jittered = delay + Math.round(delay * ratio);
        return Math.max(0L, Math.min(maxDelayMs, jittered));
    }

    private void sleepQuietly(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            // 保留中断标记，交给上层流程决定是否终止请求
            Thread.currentThread().interrupt();
        }
    }

    private int resolveInt(int preferred, int fallback, int min) {
        int value = preferred > 0 ? preferred : fallback;
        return Math.max(min, value);
    }

    private long resolveLong(long preferred, long fallback, long min) {
        long value = preferred >= 0 ? preferred : fallback;
        return Math.max(min, value);
    }

    private double resolveDouble(double preferred, double fallback, double min) {
        double value = preferred > 0 ? preferred : fallback;
        return Math.max(min, value);
    }
}
