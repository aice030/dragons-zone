package com.dragons.core.exception;

import com.dragons.core.dto.Result;
import com.dragons.core.dto.ResponseCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理业务异常，返回统一的响应格式
 * 
 * @author aice
 * @since 2026-01-18
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        if (e.getMessageOverride() != null && !e.getMessageOverride().isEmpty()) {
            return Result.error(e.getResponseCode().getCode(), e.getMessageOverride());
        }
        return Result.error(e.getResponseCode());
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 记录异常日志（这里简化处理，实际应该使用日志框架）
        e.printStackTrace();
        return Result.error(ResponseCode.INTERNAL_SERVER_ERROR);
    }
}
