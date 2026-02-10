package com.dragons.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 树洞留言分页返回（total + list）
 *
 * @author aice
 * @since 2026-02-02
 */
@Getter
@Setter
public class TreeHoleMessagePageResult {

    private Long total;
    private List<TreeHoleMessageItem> list;

    public TreeHoleMessagePageResult(Long total, List<TreeHoleMessageItem> list) {
        this.total = total;
        this.list = list;
    }

    @Getter
    @Setter
    public static class TreeHoleMessageItem {
        private Long id;
        private Long senderId;
        private String content;
        private Byte state;

        public TreeHoleMessageItem(Long id, Long senderId, String content, Byte state) {
            this.id = id;
            this.senderId = senderId;
            this.content = content;
            this.state = state;
        }
    }
}

