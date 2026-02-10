package com.dragons.core.security;

import java.io.Serializable;

/**
 * JWT解析出来的用户身份信息（最小实现）
 *
 * 放在 Spring Security 的 Authentication.principal 里，供业务代码读取 userId / loginName。
 *
 * @author aice
 * @since 2026-01-21
 */
public class JwtPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String loginName;

    public JwtPrincipal(Long userId, String loginName) {
        this.userId = userId;
        this.loginName = loginName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLoginName() {
        return loginName;
    }
}

