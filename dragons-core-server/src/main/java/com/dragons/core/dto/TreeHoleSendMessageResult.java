package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 树洞投递留言返回
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class TreeHoleSendMessageResult {

    private Long messageId;

    public TreeHoleSendMessageResult(Long messageId) {
        this.messageId = messageId;
    }
}

