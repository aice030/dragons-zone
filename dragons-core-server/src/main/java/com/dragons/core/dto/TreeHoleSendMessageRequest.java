package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 树洞投递留言请求（sent）
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class TreeHoleSendMessageRequest {

    /**
     * 留言内容
     */
    private String content;

    /**
     * 根消息ID（可选）。为空=用户投递新消息；非空=树洞主人回复该条消息（仅支持一次回复）
     */
    private Long rootMessageId;
}

