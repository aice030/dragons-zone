package com.dragons.core.exception;

import com.dragons.core.dto.Result;
import com.dragons.core.dto.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理业务异常，返回统一的响应格式
 *
 * @author aice
 * @since 2026-01-18
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        String msg = e.getMessageOverride() != null && !e.getMessageOverride().isEmpty()
                ? e.getMessageOverride() : e.getResponseCode().getMessage();
        log.warn("BusinessException: code={}, message={}", e.getResponseCode().getCode(), msg);
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
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return Result.error(ResponseCode.INTERNAL_SERVER_ERROR);
    }
}
