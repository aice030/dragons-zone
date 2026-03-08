package com.dragons.core.lock;

import com.dragons.core.cache.RedisCacheMediaCoreService;
import com.dragons.core.cache.RedisCacheMediaListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁通用模板：统一处理“获取锁 -> watchdog 续期 -> finally 释放锁”。
 *
 * 业务路径：
 * 1) prepare：按重试策略尝试加锁，构建上下文；若持锁成功则启动续期任务。
 * 2) finish：结束续期任务，并在持锁成功时释放锁。
 */
@Slf4j
@Component
public class RedisLockTemplate {

    /**
     * 共享 watchdog 调度池，避免每次加锁都创建新线程池。
     * 线程数可配置，默认 4，避免高并发下续期任务堆积。
     */
    private final ScheduledExecutorService renewScheduler;

    private final Map<RedisLockType, RedisLockOperator> operatorMap = new ConcurrentHashMap<>();

    public RedisLockTemplate(java.util.List<RedisLockOperator> operators,
                             @Value("${dragons.redis.lock.renew-pool-size:4}") int renewPoolSize) {
        int safePoolSize = Math.max(1, renewPoolSize);
        this.renewScheduler = Executors.newScheduledThreadPool(safePoolSize, r -> {
            Thread t = new Thread(r, "redis-lock-renew");
            t.setDaemon(true);
            return t;
        });
        for (RedisLockOperator operator : operators) {
            operatorMap.put(operator.type(), operator);
        }
    }

    public RedisLockExecution prepare(WithRedisLock cfg, RedisLockMeta meta) {
        validatePrepareArgs(cfg, meta);
        RedisLockOperator operator = operatorMap.get(meta.getType());
        if (operator == null) {
            throw new IllegalStateException("No RedisLockOperator found for type " + meta.getType());
        }

        String requestId = UUID.randomUUID().toString();
        boolean lockAcquired = false;

        for (int retryCount = 0; retryCount < cfg.acquireRetries(); retryCount++) {
            lockAcquired = operator.tryLock(meta, requestId);
            if (lockAcquired) {
                break;
            }
            // 不在最后一次失败后 sleep
            if (retryCount < cfg.acquireRetries() - 1) {
                sleepQuietly(cfg.retrySleepMs());
            }
        }

        RedisLockContext context = new RedisLockContext(
                meta.getType(),
                requestId,
                lockAcquired,
                meta.getMediaId(),
                meta.getZoneUserId(),
                meta.getCategory(),
                meta.getPage(),
                meta.getSize()
        );

        java.util.concurrent.ScheduledFuture<?> renewFuture = null;
        if (lockAcquired) {
            long renewPeriodMs = resolveRenewPeriodMs(cfg, meta);
            // 仅持锁成功时启动 watchdog
            renewFuture = renewScheduler.scheduleAtFixedRate(() -> {
                try {
                    boolean renewed = operator.renew(meta, requestId);
                    if (!renewed) {
                        log.warn("redis lock renew failed type={} requestId={}", meta.getType(), requestId);
                    }
                } catch (Exception e) {
                    log.warn("redis lock renew exception type={} requestId={}", meta.getType(), requestId, e);
                }
            }, 0, renewPeriodMs, TimeUnit.MILLISECONDS);
        }

        return new RedisLockExecution(operator, meta, context, renewFuture);
    }

    public void finish(RedisLockExecution execution) {
        if (execution == null) {
            return;
        }

        if (execution.getRenewFuture() != null) {
            // 先停止续期任务，再尝试释放锁
            execution.getRenewFuture().cancel(true);
        }

        if (execution.getContext().isLockAcquired()) {
            try {
                execution.getOperator().unlock(execution.getMeta(), execution.getContext().getRequestId());
            } catch (Exception e) {
                log.warn("redis lock unlock exception type={} requestId={}",
                        execution.getContext().getType(), execution.getContext().getRequestId(), e);
            }
        }
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 在进入核心流程前做参数校验，避免“错误配置导致静默降级”。
     */
    private void validatePrepareArgs(WithRedisLock cfg, RedisLockMeta meta) {
        if (cfg == null) {
            throw new IllegalArgumentException("WithRedisLock config must not be null");
        }
        if (meta == null || meta.getType() == null) {
            throw new IllegalArgumentException("RedisLockMeta/type must not be null");
        }
        if (cfg.acquireRetries() <= 0) {
            throw new IllegalArgumentException("acquireRetries must be greater than 0");
        }
        if (cfg.retrySleepMs() < 0) {
            throw new IllegalArgumentException("retrySleepMs must be greater than or equal to 0");
        }

        if (meta.getType() == RedisLockType.MEDIA_CORE) {
            if (meta.getMediaId() == null) {
                throw new IllegalArgumentException("mediaId must not be null for MEDIA_CORE lock");
            }
            return;
        }

        if (meta.getType() == RedisLockType.MEDIA_LIST) {
            if (meta.getZoneUserId() == null) {
                throw new IllegalArgumentException("zoneUserId must not be null for MEDIA_LIST lock");
            }
            if (meta.getPage() == null || meta.getSize() == null) {
                throw new IllegalArgumentException("page/size must not be null for MEDIA_LIST lock");
            }
            return;
        }

        throw new IllegalArgumentException("Unsupported lock type: " + meta.getType());
    }

    /**
     * 解析本次锁的 watchdog 续期间隔：
     * - 若注解上显式配置了 renewPeriodMs>0，则以注解为准；
     * - 否则按锁类型使用 TTL/2 的默认值。
     */
    private long resolveRenewPeriodMs(WithRedisLock cfg, RedisLockMeta meta) {
        if (cfg.renewPeriodMs() > 0) {
            return cfg.renewPeriodMs();
        }
        long ttlMs;
        if (meta.getType() == RedisLockType.MEDIA_CORE) {
            ttlMs = TimeUnit.SECONDS.toMillis(RedisCacheMediaCoreService.LOCK_TTL_SECONDS);
        } else if (meta.getType() == RedisLockType.MEDIA_LIST) {
            ttlMs = TimeUnit.SECONDS.toMillis(RedisCacheMediaListService.LOCK_TTL_SECONDS);
        } else {
            throw new IllegalArgumentException("Unsupported lock type for renewPeriodMs: " + meta.getType());
        }
        long period = ttlMs / 2;
        return period > 0 ? period : ttlMs;
    }
}
