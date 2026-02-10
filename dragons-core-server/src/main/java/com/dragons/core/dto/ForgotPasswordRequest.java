package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 未登录找回密码请求（忘记密码）
 *
 * 说明：不依赖验证码，仅通过登录名+手机号校验身份后修改密码。
 * 为避免泄露“用户是否存在”，登录名或手机号任一不匹配时统一返回相同错误。
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class ForgotPasswordRequest {

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 注册时绑定的手机号（11位）
     */
    private String phoneNumber;

    /**
     * 新密码（明文，后端会加密存储）
     */
    private String newPassword;
}
