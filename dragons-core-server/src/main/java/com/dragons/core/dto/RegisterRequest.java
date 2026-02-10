package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 注册请求DTO
 * 
 * @author aice
 * @since 2026-01-18
 */
@Getter
@Setter
public class RegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 密码
     */
    private String password;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phoneNumber;
}
