package com.dragons.core.retry;

/**
 * DB 写操作返回 false 时抛出，用于触发重试。
 *
 * 说明：
 * MyBatis-Plus 的 save/updateById 等方法在“未抛异常但影响行数为 0”时会返回 false。
 * 这类场景在业务层通常仍应视为写失败，因此转成异常交给重试切面统一处理。
 */
public class DbWriteReturnedFalseException extends RuntimeException {

    public DbWriteReturnedFalseException(String opName) {
        super("db write returned false: " + opName);
    }
}
