package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 登录请求DTO
 * 
 * @author aice
 * @since 2026-01-18
 */
@Getter
@Setter
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 密码
     */
    private String password;
}
