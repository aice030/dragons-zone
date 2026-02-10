package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 树洞消息分享请求：将一条留言分享给多个树洞主人（收件箱方）。
 * 与 media 可见列表一致：只分享给一人时传单元素列表。
 *
 * @author aice
 * @since 2026-02-xx
 */
@Getter
@Setter
public class TreeHoleShareRequest {

    /**
     * 接收方树洞主人用户 ID 列表，必填且非空
     */
    private List<Long> ownerIds;
}
