package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 通过手机号重置密码请求
 *
 * 说明：
 * - 该接口用于“忘记密码”的简化方案：只要输入正确手机号即可重置密码
 * - 适用于个人小项目的MVP阶段
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class ResetPasswordByPhoneRequest {

    /**
     * 手机号（11位）
     */
    private String phoneNumber;

    /**
     * 新密码（明文，后端会加密存储）
     */
    private String newPassword;
}

