package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 注销请求DTO
 * 
 * @author aice
 * @since 2026-01-18
 */
@Getter
@Setter
public class DeregisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 密码（用于二次确认）
     */
    private String password;
}
