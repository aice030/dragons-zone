package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 树洞状态修改请求
 *
 * state 约定（与 entity 一致）：
 * - 0：正常
 * - 2：禁止他人投递新消息
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class TreeHoleUpdateStateRequest {

    /**
     * 目标状态：0=正常；2=禁止投递
     */
    private Byte state;
}

