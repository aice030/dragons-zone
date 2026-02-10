package com.dragons.core.exception;

import com.dragons.core.dto.ResponseCode;

/**
 * 业务异常（统一）
 * 用于在 Service 层抛出可控的业务错误码，由全局异常处理器转换成 Result 返回给前端。
 *
 * @author aice
 * @since 2026-01-21
 */
public class BusinessException extends RuntimeException {

    private final ResponseCode responseCode;
    /** 可选：覆盖枚举默认文案，用于需要动态内容时（如「分享给xxx失败」） */
    private final String messageOverride;

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
        this.messageOverride = null;
    }

    /**
     * 使用指定 ResponseCode 与自定义文案（前端将看到 messageOverride，code 仍来自 responseCode）
     */
    public BusinessException(ResponseCode responseCode, String messageOverride) {
        super(messageOverride != null ? messageOverride : responseCode.getMessage());
        this.responseCode = responseCode;
        this.messageOverride = messageOverride;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public String getMessageOverride() {
        return messageOverride;
    }
}

