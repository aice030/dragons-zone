package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 树洞拉黑请求：树洞主人将某用户加入黑名单。
 *
 * @author aice
 * @since 2026-02-xx
 */
@Getter
@Setter
public class TreeHoleBlockRequest {

    /**
     * 被拉黑的用户 ID，必填
     */
    private Long blockedUserId;

    /**
     * 拉黑原因，可选
     */
    private String reason;
}
