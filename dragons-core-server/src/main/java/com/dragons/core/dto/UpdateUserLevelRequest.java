package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 修改用户等级请求
 */
@Getter
@Setter
public class UpdateUserLevelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新等级（0=作者，1=管理员，2=普通用户，3=游客）
     */
    private Byte level;
}
