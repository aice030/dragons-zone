package com.dragons.core.lock;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 线程级锁上下文容器。
 *
 * 说明：
 * - 使用“栈”保存上下文，支持同线程内的嵌套锁调用；
 * - 切面进入时 push，退出时 pop，保证外层上下文不会被内层清空；
 * - 业务代码在锁托管方法体内读取栈顶上下文。
 */
public final class RedisLockContextHolder {

    private static final ThreadLocal<Deque<RedisLockContext>> CONTEXT_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RedisLockContextHolder() {
    }

    public static void set(RedisLockContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RedisLockContext must not be null");
        }
        CONTEXT_STACK.get().push(context);
    }

    public static RedisLockContext get() {
        return CONTEXT_STACK.get().peek();
    }

    public static RedisLockContext getRequired() {
        RedisLockContext ctx = CONTEXT_STACK.get().peek();
        if (ctx == null) {
            throw new IllegalStateException("RedisLockContext is not available in current thread");
        }
        return ctx;
    }

    public static void clear() {
        Deque<RedisLockContext> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        // 栈空后移除 ThreadLocal，避免线程池线程长期持有空容器
        if (stack.isEmpty()) {
            CONTEXT_STACK.remove();
        }
    }
}
