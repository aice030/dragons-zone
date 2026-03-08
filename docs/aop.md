# AOP 应用说明

## 1. 应用场景概览


| 能力         | 注解 / 入口                | 切面                   | 典型使用处                                                     |
| ---------- | ---------------------- | -------------------- | --------------------------------------------------------- |
| DB 写入重试    | `@DbWriteRetry`        | `DbWriteRetryAspect` | 各 `*DbWriteService` 的写库方法                                 |
| Redis 分布式锁 | `@WithRedisLock`（门面方法） | `RedisLockAspect`    | `RedisLockFacade.withMediaCoreLock` / `withMediaListLock` |


---

## 2. 数据库写入重试

### 2.1 解决的问题

- 数据库写入可能因**瞬时异常**失败：死锁（`DeadlockLoserDataAccessException`）、锁等待（`CannotAcquireLockException`）、网络/超时（`TransientDataAccessException`、`SQLTransientException`、`SQLRecoverableException`）等。
- 若在业务代码中手写重试，会重复且难以统一退避策略。通过切面统一处理：**只负责“是否重试、重试次数、退避策略”**，不改变业务返回码语义。

### 2.2 实现方式

- **注解**：`@DbWriteRetry`，可配置 `maxAttempts`、`initialDelayMs`、`maxDelayMs`、`multiplier`、`jitterRatio`；未设置或 ≤0 时使用全局配置（`DbRetryProperties`，前缀 `db-retry`）。
- **切面**：`DbWriteRetryAspect`，`@Around("@annotation(dbWriteRetry)")` 拦截带注解方法。
- **可重试判定**：仅对“可重试异常”重试；**业务异常 `BusinessException` 不重试**；对 `DbWriteReturnedFalseException`（写库返回 false）视为可重试。可重试类型包括上述 JDBC/Spring 瞬时异常，沿 `cause` 链向上匹配。
- **退避**：采用指数退避并可选抖动，具体含义如下。
  - **指数退避**：第 1 次失败后等待 `initialDelayMs`，第 2 次失败后等待 `initialDelayMs * multiplier`，第 3 次再乘 `multiplier`……即第 `attempt` 次重试前的等待时间为 `initialDelayMs * multiplier^(attempt-1)`，再与 `maxDelayMs` 取 min，避免等待时间无限增大。例如 `initialDelayMs=100`、`multiplier=2`、`maxDelayMs=1000` 时，各次重试前等待约为 100ms → 200ms → 400ms → 800ms → 1000ms（之后被上限截断）。这样可以让瞬时压力下失败请求“逐渐错开”再次打库的时间，给数据库喘息空间。
  - **抖动（jitter）**：若只做指数退避，同一时刻大量请求失败后，会在同一时刻一起重试，容易再次把数据库打满（“惊群”）。因此在计算出的等待时间上加上**随机偏移**：在 `[delay * (1 - jitterRatio), delay * (1 + jitterRatio)]` 范围内随机取一个值作为实际等待时间（实现上为 `delay + delay * ratio`，`ratio` 在 `[-jitterRatio, jitterRatio]` 间随机）。例如 `jitterRatio=0.2` 时，本应等 100ms 的请求可能等 80ms～120ms，从而把重试时间打散，减轻并发峰值。

### 2.3 调用流程

1. 业务调用带 `@DbWriteRetry` 的写库方法（如 `MediaDbWriteService.insertMedia`）。
2. 代理拦截，读取注解与全局配置，确定 `maxAttempts`、退避参数。
3. 循环：执行 `pjp.proceed()`；若成功则返回；若抛异常则判断是否可重试，不可重试则直接抛出。
4. 可重试且未达最大次数：按当前尝试次数计算退避时间（含抖动），`Thread.sleep` 后进入下一轮；达到最大次数后抛出最后一次异常。

---

## 3. Redis 分布式锁

### 3.1 解决的问题

- 缓存回填、列表加载等需要**按 key 互斥**（如同一 `mediaId` 只允许一个线程回填 media:core），避免缓存击穿、重复建缓存。
- 锁的**获取、续期（watchdog）、释放**若散落在业务中，易重复、易漏释。通过切面将锁生命周期从业务中抽离：业务只关心“在持锁或未持锁下分别做什么”。

### 3.2 实现方式

- **注解**：`@WithRedisLock`，声明锁类型（`MEDIA_CORE` / `MEDIA_LIST`）及参数下标（如 `mediaIdArg`、`zoneUserIdArg`、`categoryArg`、`pageArg`、`sizeArg`），以及 `acquireRetries`、`retrySleepMs`、`renewPeriodMs`（≤0 时由模板按锁 TTL 推导，一般为 TTL/2）。
- **切面**：`RedisLockAspect`，`@Around("@annotation(withRedisLock)")` 拦截带注解方法。**不直接标在业务类上**，而是标在门面 `RedisLockFacade` 的 `withMediaCoreLock` / `withMediaListLock` 上，由门面接收业务回调。
- **模板**：`RedisLockTemplate`。  
  - **prepare**：根据 `RedisLockMeta` 选择对应 `RedisLockOperator`，按 `acquireRetries` 尝试加锁；成功则创建 `RedisLockContext`（含 `lockAcquired`）并启动**定时续期任务**（watchdog）；返回 `RedisLockExecution`。  
  - **finish**：取消续期任务，若持锁成功则调用 `operator.unlock`。
- **上下文**：切面在 `prepare` 后将 `RedisLockContext` 放入 `RedisLockContextHolder`（ThreadLocal），业务回调通过 `context.isLockAcquired()` 走持锁分支或降级分支；`finally` 中清理 ThreadLocal 并调用 `template.finish(execution)`，保证续期停止、锁释放。

### 3.3 调用流程

1. 业务调用门面，例如：`redisLockFacade.withMediaCoreLock(mediaId, lockCtx -> { ... })`。
2. 代理拦截带 `@WithRedisLock` 的门面方法，切面根据注解和参数列表解析出 `RedisLockMeta`（如 `forMediaCore(mediaId)`）。
3. 调用 `redisLockTemplate.prepare(cfg, meta)`：按重试次数尝试加锁；成功则启动 watchdog（按 TTL/2 或注解的 `renewPeriodMs` 周期续期）；构造 `RedisLockContext`（含是否持锁）和 `RedisLockExecution`。
4. 将 `context` 放入 `RedisLockContextHolder`，执行 `pjp.proceed()`，即门面方法体：`callback.execute(ctx)`，业务在回调内根据 `ctx.isLockAcquired()` 执行持锁逻辑或降级逻辑。
5. **finally**：`RedisLockContextHolder.clear()`；`redisLockTemplate.finish(execution)`：取消续期任务，若持锁则 `operator.unlock`。

---

## 4. 小结

- **DB 重试**：声明式 `@DbWriteRetry` + 切面循环执行与退避，只对可重试异常重试，业务异常直接抛出。
- **Redis 锁**：声明式 `@WithRedisLock` 标在门面方法上，切面负责“解析参数 → prepare（加锁 + 续期）→ 设上下文 → 执行业务 → finally 清理并 finish（停续期 + 释放锁）”；业务通过门面传入回调，在回调内根据 `RedisLockContext.isLockAcquired()` 分支，无需手写加锁/释放。

