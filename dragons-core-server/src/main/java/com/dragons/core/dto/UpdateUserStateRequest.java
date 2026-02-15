package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 修改用户状态请求
 */
@Getter
@Setter
public class UpdateUserStateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新状态（0=正常，1=逻辑删除，2=黑名单）
     */
    private Byte state;
}
